package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.TagType
import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.utils.CompressUtils

import org.bukkit.ChatColor

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.ceil

class MinecraftAnvilWorker {

    companion object {

        private const val HEADER_LENGTH = 8192
        private const val SECTOR_UNIT = 4096

        val g = ChatColor.GRAY
        val w = ChatColor.WHITE

        fun  readFile(target: File): Anvil<Terrain> {
            val segments = target.name.split(".")
            val regionX = segments[1].toInt()
            val regionZ = segments[2].toInt()

            return mapOf(AnvilLocation(regionX, regionZ) to target.readBytes())
                .toAnvil { _, timestamp, nbt -> Terrain(timestamp, nbt) }
        }

        fun readFiles(targets: Array<File>): AnvilFormat {
            val result = mutableMapOf<AnvilLocation, ByteArray>()

            targets.forEach {
                val segments = it.name.split(".")
                val regionX = segments[1].toInt()
                val regionZ = segments[2].toInt()

                result[AnvilLocation(regionX, regionZ)] = it.readBytes()

                Executor.sendTitle("${g}reading ${it.parentFile.name} region[$w$regionX$g][$w$regionZ$g]")
            }

            return result
        }

        inline fun <T: ChunkData> AnvilFormat.toAnvil(generator: (ChunkLocation, Int, CompoundTag) -> T): Anvil<T> {
            val result = mutableMapOf<AnvilLocation, List<T>>()

            entries.forEach { (anvilLocation, bytes) ->
                val parts = mutableListOf<T>()

                for (m in 0 until 32 * 32) {
                    val x = m / 32
                    val z = m % 32
                    val i = 4 * ((x and 31) + (z and 31) * 32)

                    val (timestamp, buffer) = decompress(bytes, i) ?: continue

                    parts.add(generator.invoke(ChunkLocation(32 * anvilLocation.x + x, 32 * anvilLocation.z + z), timestamp, Tag.read(TagType.TAG_COMPOUND, buffer, null).getAs()))
                }

                result[anvilLocation] = parts
            }

            return result
        }

        fun WorldAnvil.toWorldAnvilFormat(): WorldAnvilFormat {
            return mapOf(
                AnvilType.ENTITY to entity.toAnvilFormat(),
                AnvilType.POI to poi.toAnvilFormat()
            )
        }

        fun <T: ChunkData> Anvil<T>.toAnvilFormat(): AnvilFormat {
            val result = mutableMapOf<AnvilLocation, ByteArray>()

            entries.forEach { (anvilLocation, dataList) ->
                val locationHeader = ByteArray(4096)
                val timestampsHeader = ByteArray(4096)

                val stream = ByteArrayOutputStream()

                var sector = HEADER_LENGTH / SECTOR_UNIT

                dataList.forEach { data ->
                    val headerOffset = 4 * (((data.location.x - 32 * anvilLocation.x) and 31) + ((data.location.z - 32 * anvilLocation.z) and 31) * 32)

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
            }

            return result
        }

        fun decompress(region: ByteArray, headerOffset: Int): Pair<Int, ByteBuffer>? {
            if (region.isEmpty()) return null

            val offset = ByteBuffer.wrap(byteArrayOf(0, region[headerOffset], region[headerOffset + 1], region[headerOffset + 2])).int * 4096
            val sectorCount = ByteBuffer.wrap(byteArrayOf(0, 0, 0, region[headerOffset + 3])).int * 4096

            val timestamp = ByteBuffer.wrap(region.slice(4096 + headerOffset until 4096 + headerOffset + 4).toByteArray()).int

            if (offset == 0 || sectorCount == 0) return null

            val data = region.slice(offset until offset + sectorCount)
            val size = ByteBuffer.wrap(data.slice(0 until 4).toByteArray()).int

            val compressionType = data[4]
            val compressed = data.slice(5 until 5 + size - 1).toByteArray()

            if (compressionType.toInt() != 2) throw Exception("unsupported compression type '$compressionType'")

            val chunkBuffer = ByteBuffer.wrap(CompressUtils.ZLib.decompress(compressed))
            chunkBuffer.byte
            chunkBuffer.short

            return Pair(timestamp, chunkBuffer)
        }

        private fun compress(tag: CompoundTag): ByteArray {
            val buffer = ByteBuffer.allocate(Byte.SIZE_BYTES + Short.SIZE_BYTES + tag.sizeInBytes)
            tag.ensureName(null).getAs<CompoundTag>().writeAsRoot(buffer)

            val compressedData = CompressUtils.ZLib.compress(buffer.array())
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