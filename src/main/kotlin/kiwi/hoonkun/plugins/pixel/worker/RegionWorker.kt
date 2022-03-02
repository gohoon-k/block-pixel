package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor

import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.tag.*

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

        internal fun Regions.toClientRegions(): ClientRegions {
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

        internal fun ClientRegions.toRegions(): Regions {
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

    }

}