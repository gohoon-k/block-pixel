package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Blocks
import kiwi.hoonkun.plugins.pixel.BlocksRaw
import kiwi.hoonkun.plugins.pixel.nbt.extensions.indent
import kiwi.hoonkun.plugins.pixel.nbt.tag.LongArrayTag
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

            return map { long ->
                val bits = (0 until Long.SIZE_BITS).map { long.takeBit(it) == 1 }
                bits.chunked(bitsPerBlock)
                    .filter { it.size == bitsPerBlock }
                    .map { it.mapIndexed { index, bit -> if (bit) 2.0.pow(index).toInt() else 0 }.sum() }
                    .toTypedArray()
            }.toTypedArray().flatten().take(4096).toIntArray()
        }

        fun Blocks.pack(paletteSize: Int): BlocksRaw {
            val bitsPerBlock = size(paletteSize)
            val blocksPerLong = Long.SIZE_BITS / bitsPerBlock

            val paddings = BooleanArray(Long.SIZE_BITS % bitsPerBlock) { false }.toList()

            return toList().chunked(blocksPerLong).map { ints ->
                val bits = ints.map { int -> (0 until bitsPerBlock).map { int.takeBit(it) == 1 }.toTypedArray() }
                    .toTypedArray()
                    .flatten()
                    .toMutableList()
                bits.addAll(paddings)
                bits.mapIndexed { index, bit ->
                    if (bit) { if (index == Long.SIZE_BITS - 1) Long.MIN_VALUE else 2.0.pow(index).toLong() } else 0
                }.sum()
            }.toLongArray()
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

        private fun Long.takeBit(position: Int): Int {
            return ((this shr position) and 1).toInt()
        }

        private fun Int.takeBit(position: Int): Int {
            return (this shr position) and 1
        }

    }

}