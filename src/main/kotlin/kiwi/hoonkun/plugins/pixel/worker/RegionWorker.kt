package kiwi.hoonkun.plugins.pixel.worker

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kiwi.hoonkun.plugins.pixel.*

import kiwi.hoonkun.plugins.pixel.nbt.AnyTag
import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.extensions.indent
import kiwi.hoonkun.plugins.pixel.nbt.tag.*
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.toUnpackedString

import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

import kotlin.math.ceil

class RegionWorker {

    companion object {

        private const val HEADER_LENGTH = 8192
        private const val SECTOR_UNIT = 4096

        fun ClientRegionFiles.readClientRegions(): ClientRegions {
            val result = mutableMapOf<RegionLocation, ByteArray>()

            get.forEach {
                val segments = it.name.split(".")
                val regionX = segments[1].toInt()
                val regionZ = segments[2].toInt()

                result[RegionLocation(regionX, regionZ)] = it.readBytes()
            }

            return ClientRegions(result)
        }

        fun VersionedRegionFiles.readVersionedRegions(): VersionedRegions {
            val temp = mutableMapOf<RegionLocation, MutableVersionedRegion>()

            get.forEach {
                val segments = it.name.split(".")
                val regionX = segments[1].toInt()
                val regionZ = segments[2].toInt()

                val location = RegionLocation(regionX, regionZ)
                if (!temp.containsKey(location)) temp[location] = MutableVersionedRegion()

                when (segments[4]) {
                    "t" -> temp[location]!!.types = String(it.readBytes())
                    "d" -> temp[location]!!.data = String(it.readBytes())
                }
            }

            return VersionedRegions(temp.map { (k, v) -> k to v.toVersionedRegion() }.toMap())
        }

        fun ClientRegions.toVersionedRegions(): VersionedRegions = toRegions().toVersionedRegions()

        fun VersionedRegions.toClientRegions(original: ClientRegionFiles): ClientRegions = toRegions(original).toClientRegions()

        internal fun Regions.toClientRegions(): ClientRegions {
            val result = mutableMapOf<RegionLocation, ByteArray>()

            get.entries.forEach { (regionLocation, chunks) ->

                val locationHeader = ByteArray(4096)
                val timestampsHeader = ByteArray(4096)

                val stream = ByteArrayOutputStream()

                var sector = HEADER_LENGTH / SECTOR_UNIT

                chunks.forEach { chunk ->
                    val chunkX = chunk.nbt.value["xPos"]?.getAs<IntTag>()?.value ?: throw Exception("could not find value 'xPos'")
                    val chunkZ = chunk.nbt.value["zPos"]?.getAs<IntTag>()?.value ?: throw Exception("could not find value 'zPos'")

                    val headerOffset = 4 * ((chunkX and 31) + (chunkZ and 31) * 32)

                    val compressedNbt = compress(chunk.nbt)

                    val offset = ByteArray (4) { i -> (sector shr ((3 - i) * 8)).toByte() }
                    val sectorCount = ceil(compressedNbt.size / SECTOR_UNIT.toFloat()).toInt().toByte()

                    val location = byteArrayOf(offset[1], offset[2], offset[3], sectorCount)
                    val timestamp = ByteArray (4) { i -> (chunk.timestamp shr ((3 - i)*8)).toByte() }

                    location.forEachIndexed { index, byte -> locationHeader[headerOffset + index] = byte }
                    timestamp.forEachIndexed { index, byte -> timestampsHeader[headerOffset + index] = byte }

                    stream.write(compressedNbt)

                    sector += sectorCount
                }

                val regionStream = ByteArrayOutputStream()
                regionStream.write(locationHeader)
                regionStream.write(timestampsHeader)
                regionStream.write(stream.toByteArray())

                result[regionLocation] = regionStream.toByteArray()
            }

            return ClientRegions(result)
        }

        internal fun Regions.toVersionedRegions(): VersionedRegions {
            val result = mutableMapOf<RegionLocation, VersionedRegion>()

            get.entries.forEach { (location, chunks) ->
                val versionedChunks = chunks.map { chunk ->
                    val blockEntities = chunk.nbt["block_entities"]!!.getAs<ListTag>()

                    val sections = chunk.nbt["sections"]!!.getAs<ListTag>().value.map generateSections@ { sectionTag ->
                        val section = sectionTag.getAs<CompoundTag>()
                        val blockStates = section["block_states"]!!.getAs<CompoundTag>()

                        val yString = "${section["Y"]!!}".indent()

                        val palette = blockStates["palette"]!!.getAs<ListTag>()
                        val paletteString = "$palette".indent()

                        val data = blockStates["data"]?.getAs<LongArrayTag>() ?: LongArrayTag(longArrayOf(), "data")
                        val dataString = "\"data\": ${data.toUnpackedString(palette.value.size)}".indent()

                        return@generateSections "{\n$yString,\n$paletteString,\n$dataString\n}".indent()
                    }

                    val x = "\"X\": ${chunk.nbt["xPos"]!!.getAs<IntTag>().value}".indent()
                    val z = "\"Z\": ${chunk.nbt["zPos"]!!.getAs<IntTag>().value}".indent()
                    val sectionsString = "\"sections\": [\n${sections.joinToString(",\n")}\n]".indent()
                    val blockEntitiesString = blockEntities.toString().indent()
                    val typesString = Gson().toJson(blockEntities.generateTypes("block_entities"))

                    VersionedRegion("{\n$x,\n$z,\n$sectionsString,\n$blockEntitiesString\n}", typesString)
                }

                result[location] = VersionedRegion(
                    "[\n${versionedChunks.joinToString(",\n"){ it.data }.indent()}\n]",
                    "[\n${versionedChunks.joinToString(",\n") { it.types }.indent()}\n]"
                )
            }
            return VersionedRegions(result)
        }

        internal fun ClientRegions.toRegions(): Regions {
            val result = mutableMapOf<RegionLocation, List<Chunk>>()

            get.entries.forEach { (regionLocation, bytes) ->
                val chunks = mutableListOf<Chunk>()

                for (m in 0 until 32 * 32) {
                    val i = 4 * (((m / 32) and 31) + ((m % 32) and 31) * 32)

                    val (timestamp, buffer) = decompress(bytes, i) ?: continue

                    chunks.add(Chunk(timestamp, Tag.read(TagType.TAG_COMPOUND, buffer, null).getAs()))
                }

                result[regionLocation] = chunks
            }

            return Regions(result)
        }

        internal fun VersionedRegions.toRegions(original: ClientRegionFiles): Regions {
            val originalRegions = original.readClientRegions().toRegions().toMutableRegions()

            val chunksType = object : TypeToken<Array<VersionedChunk>>() {}.type
            val typesType = object : TypeToken<Array<Map<String, Byte>>>() {}.type

            get.entries.forEach { (location, versionedRegion) ->
                val blockEntityTypes = Gson().fromJson<Array<Map<String, Byte>>>(
                    versionedRegion.types, typesType
                )

                val gson = GsonBuilder()
                    .registerTypeAdapter(chunksType, GitChunkDeserializer(blockEntityTypes))
                    .create()

                val versionedChunks = gson.fromJson<Array<VersionedChunk>>(versionedRegion.data, chunksType)

                val region = originalRegions[location] ?: return@forEach

                originalRegions[location] = versionedChunks.map { versionedChunk ->
                    val chunk = region.findChunk(versionedChunk.X, versionedChunk.Z)
                        ?: throw Exception("cannot find chunk (${versionedChunk.X}, ${versionedChunk.Z}) from region (${location.x}, ${location.z})")

                    chunk.nbt["sections"]!!.getAs<ListTag>().value = versionedChunk.sections.map { section -> section.toNbt() }
                    chunk.nbt["block_entities"]!!.getAs<ListTag>().value = versionedChunk.block_entities.toList()
                    chunk
                }
            }
            return originalRegions.toImmutableRegions()
        }

        private fun decompress(region: ByteArray, headerOffset: Int): Pair<Int, ByteBuffer>? {
            val offset = ByteBuffer.wrap(byteArrayOf(0, region[headerOffset], region[headerOffset + 1], region[headerOffset + 2])).int * 4096
            val sectorCount = ByteBuffer.wrap(byteArrayOf(0, 0, 0, region[headerOffset + 3])).int * 4096

            val timestamp = ByteBuffer.wrap(region.slice(4096 + headerOffset until 4096 + headerOffset + 4).toByteArray()).int

            if (offset == 0 || sectorCount == 0) return null

            val data = region.slice(offset until offset + sectorCount)
            val size = ByteBuffer.wrap(data.slice(0 until 4).toByteArray()).int

            val compressionType = data[4]
            val compressed = data.slice(5 until 5 + size - 1).toByteArray()

            if (compressionType.toInt() != 2) throw Exception("unsupported compression type '$compressionType'")

            val inflater = Inflater()
            inflater.setInput(compressed)

            val outputArray = ByteArray(1024)
            val stream = ByteArrayOutputStream(compressed.size)
            while (!inflater.finished()) {
                val count = inflater.inflate(outputArray)
                stream.write(outputArray, 0, count)
            }

            val chunk = stream.toByteArray()
            stream.close()

            val chunkBuffer = ByteBuffer.wrap(chunk)
            chunkBuffer.byte
            chunkBuffer.short

            return Pair(timestamp, chunkBuffer)
        }

        private fun compress(tag: CompoundTag): ByteArray {
            val buffer = ByteBuffer.allocate(Byte.SIZE_BYTES + Short.SIZE_BYTES + tag.sizeInBytes)
            tag.ensureName(null).getAs<CompoundTag>().writeAsRoot(buffer)

            val deflater = Deflater()
            deflater.setInput(buffer.array())
            deflater.finish()

            val compressTemplate = ByteArray(1024)
            val compressStream = ByteArrayOutputStream()

            while (!deflater.finished()) {
                val count = deflater.deflate(compressTemplate)
                compressStream.write(compressTemplate, 0, count)
            }

            val compressedData = compressStream.toByteArray()
            val compressionScheme = 2

            val length = compressedData.size + 1

            val result = ByteBuffer.allocate(padding(4 + length))
            result.putInt(length)
            result.put(compressionScheme.toByte())
            result.put(compressedData)

            return result.array()
        }

        private fun padding(size: Int): Int {
            var index = 0
            while (size > index * 4096) index++

            return index * 4096
        }

        private fun VersionedSection.toNbt(): CompoundTag {
            val yTag = IntTag(Y, "Y")
            val paletteTag = ListTag(
                TagType.TAG_COMPOUND,
                palette.map {
                    val compound = mutableMapOf<String, AnyTag>()
                    compound["Name"] = StringTag(it.Name, "Name")

                    if (it.Properties != null) compound["Properties"] = it.Properties

                    CompoundTag(compound)
                },
                true,
                "palette"
            )
            val dataTag = LongArrayTag(data.toIntArray().pack(palette.size), "data")

            val blockStatesTag = CompoundTag(mapOf("palette" to paletteTag, "data" to dataTag), "block_states")

            return CompoundTag(mapOf("Y" to yTag, "block_states" to blockStatesTag), null)
        }

        class GitChunkDeserializer(
            private val types: Array<Map<String, Byte>>,
        ): JsonDeserializer<Array<VersionedChunk>> {

            override fun deserialize(
                json: JsonElement?,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): Array<VersionedChunk> {
                json ?: throw Exception("cannot find json element of block entity")

                if (!json.isJsonArray) throw Exception("json element 'block_entity' is not a json array")

                val sectionsType = object : TypeToken<List<VersionedSection>>() {}.type
                val palettePropertiesType = object : TypeToken<CompoundTag>() {}.type

                val gson = GsonBuilder()
                    .registerTypeAdapter(palettePropertiesType, PropertiesDeserializer())
                    .create()

                return json.asJsonArray.mapIndexed { index, item ->
                    val chunkItem = if (item.isJsonObject) item.asJsonObject else throw Exception("json element in json array is not a json object")

                    val x = chunkItem["X"]?.asInt ?: throw Exception("cannot find field 'X'")
                    val z = chunkItem["Z"]?.asInt ?: throw Exception("cannot find field 'Z'")
                    val sections = gson.fromJson<List<VersionedSection>>(chunkItem["sections"], sectionsType)
                    val blockEntities = BlockEntityDeserializer(types[index]).deserialize(chunkItem["block_entities"], null, null)

                    VersionedChunk(x, z, sections, blockEntities, types[index])
                }.toTypedArray()
            }

        }

        class BlockEntityDeserializer(private val types: Map<String, Byte>): JsonDeserializer<List<CompoundTag>> {

            override fun deserialize(
                json: JsonElement?,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): List<CompoundTag> {
                json ?: throw Exception("cannot find json element of block entity")

                if (!json.isJsonArray) throw Exception("json element 'block_entity' is not a json array")

                return json.asJsonArray.mapIndexed { index, element ->
                    element.asJsonObject.asCompoundTag("block_entities[$index]")
                }
            }

            private fun JsonObject.asCompoundTag(parentPath: String): CompoundTag {
                val result = mutableMapOf<String, AnyTag>()
                entrySet().map { (jsonName, jsonValue) ->
                    val path = "$parentPath.$jsonName"
                    val type = types[path] ?: throw Exception("cannot find type of '$path'")

                    if (TagType[type] == TagType.TAG_COMPOUND || jsonValue.isJsonObject) {
                        result[jsonName] = jsonValue.asJsonObject.asCompoundTag(path)
                    } else if (TagType[type] == TagType.TAG_LIST) {
                        val elementsType = types["$path.*"] ?: throw Exception("cannot find elements type of list '$path'")
                        if (elementsType == TagType.TAG_COMPOUND.id) {
                            result[jsonName] = ListTag(
                                TagType[elementsType],
                                jsonValue.asJsonArray.mapIndexed { index, item -> item.asJsonObject.asCompoundTag("$path[$index]") }
                            )
                        } else {
                            result[jsonName] = ListTag(
                                TagType[elementsType],
                                jsonValue.asJsonArray.map { createTag(TagType[elementsType], null, it) },
                                true,
                                jsonName
                            )
                        }
                    } else {
                        result[jsonName] = createTag(TagType[type], jsonName, jsonValue)
                    }
                }
                return CompoundTag(result)
            }

            private fun createTag(type: TagType, jsonName: String?, jsonValue: JsonElement): AnyTag {
                return when (type) {
                    TagType.TAG_BYTE -> ByteTag(jsonValue.asByte, jsonName)
                    TagType.TAG_SHORT -> ShortTag(jsonValue.asShort, jsonName)
                    TagType.TAG_DOUBLE -> DoubleTag(jsonValue.asDouble, jsonName)
                    TagType.TAG_INT -> IntTag(jsonValue.asInt, jsonName)
                    TagType.TAG_LONG -> LongTag(jsonValue.asLong, jsonName)
                    TagType.TAG_FLOAT -> FloatTag(jsonValue.asFloat, jsonName)
                    TagType.TAG_STRING -> StringTag(jsonValue.asString, jsonName)
                    TagType.TAG_BYTE_ARRAY -> ByteArrayTag(jsonValue.asJsonArray.map { it.asByte }.toByteArray(), jsonName)
                    TagType.TAG_INT_ARRAY -> IntArrayTag(jsonValue.asJsonArray.map { it.asInt }.toIntArray(), jsonName)
                    TagType.TAG_LONG_ARRAY -> LongArrayTag(jsonValue.asJsonArray.map { it.asLong }.toLongArray(), jsonName)
                    else -> throw Exception("cannot create tag of compound, list, end in this context with name '$jsonName(${type.name})'")
                }
            }

        }

        class PropertiesDeserializer: JsonDeserializer<CompoundTag> {

            override fun deserialize(
                json: JsonElement?,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): CompoundTag {
                json ?: throw Exception("cannot find json element of palette properties")

                if (!json.isJsonObject) throw Exception("json element 'Properties' is not a json object")

                return CompoundTag(json.asJsonObject.entrySet().associate { (name, value) -> name to StringTag(value.asString, name) }, "Properties")
            }

        }

    }

}