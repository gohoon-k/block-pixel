package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.nbt.extensions.byte
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

class MinecraftAnvilWorker {

    companion object {

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

        fun compress(tag: CompoundTag): ByteArray {
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