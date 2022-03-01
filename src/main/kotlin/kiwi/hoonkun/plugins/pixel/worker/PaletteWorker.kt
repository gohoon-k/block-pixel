package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Blocks
import kiwi.hoonkun.plugins.pixel.BlocksRaw
import kiwi.hoonkun.plugins.pixel.nbt.extensions.indent
import kiwi.hoonkun.plugins.pixel.nbt.tag.LongArrayTag
import kotlin.math.ceil
import kotlin.math.pow

class PaletteWorker {

    companion object {

        fun LongArrayTag.toUnpackedString(paletteSize: Int): String {
            val unpacked = value.unpack(paletteSize)
            var result = "["
            if (unpacked.isNotEmpty()) {
                result += "\n${unpacked.joinToString(",").indent()}\n"
            }
            result += "]"
            return result
        }

        fun BlocksRaw.unpack(paletteSize: Int): Blocks {
            val bitsPerBlock = size(paletteSize)
            val bitMask = (2.0).pow(bitsPerBlock).toLong() - 1L
            val blocksPerLong = Long.SIZE_BITS / bitsPerBlock

            val result = mutableListOf<Int>()

            forEachIndexed { index, long ->
                var remaining = long
                for (block in 0 until(Long.SIZE_BITS / bitsPerBlock)) {
                    result.add(index * blocksPerLong, (remaining and bitMask).toInt())
                    remaining = remaining shr bitsPerBlock

                    if (result.size == 4096) return@forEachIndexed
                }
            }

            return result
        }

        fun Blocks.pack(paletteSize: Int): BlocksRaw {
            val bitsPerBlock = size(paletteSize)
            val blocksPerLong = Long.SIZE_BITS / bitsPerBlock
            val totalLength = ceil(4096.0f / blocksPerLong).toInt()

            return LongArray(totalLength) { index ->
                var long = 0L

                for (block in index * blocksPerLong until (index + 1) * blocksPerLong) {
                    if (block >= 4096) break
                    if (block != index * blocksPerLong) long = long shl bitsPerBlock
                    long = long or get(block).toLong()
                }

                long
            }
        }

        private fun size(size: Int): Int {
            var result = 4
            var value = 2 * 2 * 2 * 2
            while (size > value) {
                value *= 2
                result++
            }
            return result
        }

    }

}