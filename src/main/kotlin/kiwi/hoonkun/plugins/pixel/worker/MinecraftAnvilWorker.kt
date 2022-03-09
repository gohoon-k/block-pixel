package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag

import org.bukkit.ChatColor

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.ceil

class MinecraftAnvilWorker {

    companion object {

        private const val HEADER_LENGTH = 8192
        private const val SECTOR_UNIT = 4096

        val dg = ChatColor.DARK_GRAY
        val g = ChatColor.GRAY
        val w = ChatColor.WHITE

        fun Array<File>.read(): AnvilFormat {
            val result = mutableMapOf<AnvilLocation, ByteArray>()

            forEach {
                val start = System.currentTimeMillis()

                val segments = it.name.split(".")
                val regionX = segments[1].toInt()
                val regionZ = segments[2].toInt()

                result[AnvilLocation(regionX, regionZ)] = it.readBytes()

                Executor.sendTitle("${g}reading client anvil ${w}${it.name}${g} finished${dg} in ${System.currentTimeMillis() - start}ms")
            }

            return result
        }

        inline fun <T: ChunkData> AnvilFormat.toAnvil(generator: (ChunkLocation, Int, CompoundTag) -> T): Anvil<T> {
            val result = mutableMapOf<AnvilLocation, List<T>>()

            entries.forEach { (anvilLocation, bytes) ->
                val start = System.currentTimeMillis()

                val parts = mutableListOf<T>()

                for (m in 0 until 32 * 32) {
                    val x = m / 32
                    val z = m % 32
                    val i = 4 * ((x and 31) + (z and 31) * 32)

                    val (timestamp, buffer) = decompress(bytes, i) ?: continue

                    parts.add(generator.invoke(ChunkLocation(x, z), timestamp, Tag.read(TagType.TAG_COMPOUND, buffer, null).getAs()))
                }

                result[anvilLocation] = parts

                Executor.sendTitle("generating nbt $g[$w${anvilLocation.x}$g][$w${anvilLocation.z}$g]$w finished$dg in ${System.currentTimeMillis() - start}")
            }

            return result
        }

        fun WorldAnvil.toWorldAnvilFormat(): WorldAnvilFormat {
            return mapOf(
                AnvilType.TERRAIN to terrain.toAnvilFormat(),
                AnvilType.ENTITY to entity.toAnvilFormat(),
                AnvilType.POI to poi.toAnvilFormat()
            )
        }

        fun <T: ChunkData> Anvil<T>.toAnvilFormat(): AnvilFormat {
            val result = mutableMapOf<AnvilLocation, ByteArray>()

            entries.forEach { (anvilLocation, dataList) ->
                val regionStart = System.currentTimeMillis()

                val locationHeader = ByteArray(4096)
                val timestampsHeader = ByteArray(4096)

                val stream = ByteArrayOutputStream()

                var sector = HEADER_LENGTH / SECTOR_UNIT

                dataList.forEach { data ->
                    val headerOffset = 4 * ((data.location.x and 31) + (data.location.z and 31) * 32)

                    val compressedNbt = compress(data.nbt)

                    val offset = ByteArray (4) { i -> (sector shr ((3 - i) * 8)).toByte() }
                    val sectorCount = ceil(compressedNbt.size / SECTOR_UNIT.toFloat()).toInt().toByte()

                    val location = byteArrayOf(offset[1], offset[2], offset[3], sectorCount)
                    val timestamp = ByteArray (4) { i -> (data.timestamp shr ((3 - i)*8)).toByte() }

                    location.forEachIndexed { index, byte -> locationHeader[headerOffset + index] = byte }
                    timestamp.forEachIndexed { index, byte -> timestampsHeader[headerOffset + index] = byte }

                    stream.write(compressedNbt)

                    sector += sectorCount
                }

                val regionStream = ByteArrayOutputStream()
                regionStream.write(locationHeader)
                regionStream.write(timestampsHeader)
                regionStream.write(stream.toByteArray())

                result[anvilLocation] = regionStream.toByteArray()

                Executor.sendTitle("generating client region ${g}[${w}${anvilLocation.x}${g}][${w}${anvilLocation.z}${g}]${w} finished${dg} in ${System.currentTimeMillis() - regionStart}")
            }

            return result
        }

        fun decompress(region: ByteArray, headerOffset: Int): Pair<Int, ByteBuffer>? {
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