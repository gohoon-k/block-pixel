package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.tag.*
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.unpack

import java.io.ByteArrayOutputStream
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
                val start = System.currentTimeMillis()

                val segments = it.name.split(".")
                val regionX = segments[1].toInt()
                val regionZ = segments[2].toInt()

                result[RegionLocation(regionX, regionZ)] = it.readBytes()

                Executor.sendTitle("reading client region ${it.name} finished in ${System.currentTimeMillis() - start}ms")
            }

            return ClientRegions(result)
        }

        fun Regions.toClientRegions(): ClientRegions {
            val result = mutableMapOf<RegionLocation, ByteArray>()

            get.entries.forEach { (regionLocation, chunks) ->
                val regionStart = System.currentTimeMillis()

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

                Executor.sendTitle("generating client region [${regionLocation.x}, ${regionLocation.z}] finished in ${System.currentTimeMillis() - regionStart}")
            }

            return ClientRegions(result)
        }

        fun ClientRegions.toRegions(): Regions {
            val result = mutableMapOf<RegionLocation, List<Chunk>>()

            get.entries.forEach { (regionLocation, bytes) ->
                val start = System.currentTimeMillis()

                val chunks = mutableListOf<Chunk>()

                for (m in 0 until 32 * 32) {
                    val i = 4 * (((m / 32) and 31) + ((m % 32) and 31) * 32)

                    val (timestamp, buffer) = decompress(bytes, i) ?: continue

                    chunks.add(Chunk(timestamp, Tag.read(TagType.TAG_COMPOUND, buffer, null).getAs()))
                }

                result[regionLocation] = chunks

                Executor.sendTitle("generating region [${regionLocation.x}, ${regionLocation.z}] finished in ${System.currentTimeMillis() - start}")
            }

            return Regions(result)
        }

        fun merge(from: Regions, into: Regions, ancestor: Regions, mode: MergeMode): Regions {
            val merged = mutableMapOf<RegionLocation, List<Chunk>>()

            val (new, already) = from.get.entries.classificationByBoolean { !into.get.containsKey(it.key) }
            if (mode == MergeMode.REPLACE)
                new.forEach { merged[it.key] = it.value }

            already.map { it.key }
                .forEach { location ->
                    Executor.sendTitle("merging region[${location.x}, ${location.z}]")

                    val mergedChunks = mutableListOf<Chunk>()
                    associateChunk(
                        from.get[location],
                        into.get[location],
                        ancestor.get[location]
                    ).forEach { associatedMap ->
                        val associated = associatedMap.value
                        Executor.sendTitle("merging region[${location.x}, ${location.z}].chunk[${associatedMap.key.x}, ${associatedMap.key.z}]")
                        val fromC = associated.from
                        val intoC = associated.into
                        val anceC = associated.ancestor
                        if (fromC == null && intoC != null) {
                            mergedChunks.add(intoC)
                        } else if (fromC != null && intoC != null) {
                            val resultC = Chunk(intoC.timestamp, intoC.nbt.clone(intoC.nbt.name))

                            val resultE = mutableListOf<BlockEntity>()
                            val intoE = intoC.blockEntities
                            val fromE = fromC.blockEntities
                            val anceE = anceC?.blockEntities

                            (0 until intoC.sections.size).forEach { sectionIndex ->
                                val fromS = fromC.sections[sectionIndex]
                                val intoS = intoC.sections[sectionIndex]
                                val anceS = anceC?.sections?.get(sectionIndex)

                                val fromP = fromS.blockStates.palette
                                val fromM = fromS.blockStates.data.unpack(fromP.size).map { fromP[it] }

                                val intoP = intoS.blockStates.palette
                                val intoM = intoS.blockStates.data.unpack(intoP.size).map { intoP[it] }

                                val anceP = anceS?.blockStates?.palette
                                val anceM = if (anceP != null) anceS.blockStates.data.unpack(anceP.size).map { anceP[it] } else null

                                val resultP = mutableListOf<Palette>()

                                (0 until 4096).forEach { block ->
                                    val (x, y, z) = coordinate(intoC.location, intoS.y, block)

                                    val applyIt: (Palette, List<BlockEntity>) -> Unit = { applyB, applyE ->
                                        resultP.add(applyB)
                                        applyE.find { it.x == x && it.z == z && it.y == y }
                                            ?.also { resultE.add(it) }
                                    }

                                    val blockEquals: (Palette, BlockEntity?, Palette, BlockEntity?) -> Boolean = e@ { p1, be1, p2, be2 ->
                                        if (p1 != p2) return@e false
                                        if (be1 != null || be2 != null) return@e false

                                        return@e true
                                    }

                                    val fromB = if (fromM.isEmpty()) fromP[0] else fromM[block]
                                    val fromBE = fromE.find { it.x == x && it.z == z && it.y == y }
                                    val intoB = if (intoM.isEmpty()) intoP[0] else intoM[block]
                                    val intoBE = intoE.find { it.x == x && it.z == z && it.y == y }

                                    if (anceS != null && anceP != null && anceM != null) {
                                        val anceB = if (anceM.isEmpty()) anceP[0] else anceM[block]
                                        val anceBE = anceE?.find { it.x == x && it.z == z && it.y == y }

                                        if (
                                            !blockEquals(fromB, fromBE, intoB, intoBE) &&
                                            !blockEquals(fromB, fromBE, anceB, anceBE) &&
                                            !blockEquals(anceB, anceBE, intoB, intoBE)
                                        ) {
                                            if (mode == MergeMode.KEEP) {
                                                applyIt(intoB, intoE)
                                            } else if (mode == MergeMode.REPLACE) {
                                                applyIt(fromB, fromE)
                                            }
                                        } else if (
                                            blockEquals(fromB, fromBE, anceB, anceBE) && !blockEquals(fromB, fromBE, intoB, intoBE) ||
                                            blockEquals(anceB, anceBE, intoB, intoBE) && !blockEquals(fromB, fromBE, intoB, intoBE)
                                        ) {
                                            if (fromB == anceB) {
                                                applyIt(intoB, intoE)
                                            } else if (intoB == anceB) {
                                                applyIt(fromB, fromE)
                                            }
                                        } else {
                                            applyIt(intoB, intoE)
                                        }
                                    } else {
                                        if (!blockEquals(fromB, fromBE, intoB, intoBE)) {
                                            if (mode == MergeMode.KEEP) {
                                                applyIt(intoB, intoE)
                                            } else if (mode == MergeMode.REPLACE) {
                                                applyIt(fromB, fromE)
                                            }
                                        } else {
                                            applyIt(intoB, intoE)
                                        }
                                    }
                                }
                                val resultPS = resultP.toSet().toList()
                                val resultD =
                                    if (resultPS.size != 1) resultP.map { resultPS.indexOf(it) }.pack(resultPS.size)
                                    else LongArray(0)

                                resultC.sections[sectionIndex].blockStates.data = resultD
                                resultC.sections[sectionIndex].blockStates.palette = resultPS
                            }

                            resultC.blockEntities = resultE

                            mergedChunks.add(resultC)
                        }
                    }
                    merged[location] = mergedChunks
                }

            Executor.sendTitle("all regions merged")

            return Regions(merged)
        }

        private inline fun <T>Collection<T>.classificationByBoolean(criteria: (value: T) -> Boolean): Pair<List<T>, List<T>> {
            val a = mutableListOf<T>()
            val b = mutableListOf<T>()
            forEach {
                if (criteria(it)) a.add(it)
                else b.add(it)
            }
            return Pair(a, b)
        }

        private fun associateChunk(from: List<Chunk>?, into: List<Chunk>?, ancestor: List<Chunk>?): Map<ChunkLocation, AssociatedChunk> {
            val chunkMap = mutableMapOf<ChunkLocation, AssociatedChunk>()
            from?.forEach {
                if (!chunkMap.containsKey(it.location)) chunkMap[it.location] = AssociatedChunk()
                chunkMap.getValue(it.location).from = it
            }
            into?.forEach {
                if (!chunkMap.containsKey(it.location)) chunkMap[it.location] = AssociatedChunk()
                chunkMap.getValue(it.location).into = it
            }
            ancestor?.forEach {
                if (!chunkMap.containsKey(it.location)) chunkMap[it.location] = AssociatedChunk()
                chunkMap.getValue(it.location).ancestor = it
            }

            return chunkMap
        }

        private data class AssociatedChunk(var from: Chunk? = null, var into: Chunk? = null, var ancestor: Chunk? = null)

        private fun coordinate(location: ChunkLocation, sectionY: Byte, blockIndex: Int): Triple<Int, Int, Int> {
            val x = location.x
            val y = sectionY.toInt()
            val z = location.z
            return Triple(
                x * 16 + (blockIndex % 16),
                y * 16 + ((blockIndex / (16 * 16)) % 16),
                z * 16 + ((blockIndex / 16) % 16)
            )
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

        enum class MergeMode {
            KEEP, REPLACE
        }

    }

}