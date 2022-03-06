package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.PaletteWorker.Companion.unpack

import kotlinx.coroutines.delay

import org.bukkit.ChatColor


class RegionWorker {

    companion object {

        private val g = ChatColor.GRAY
        private val w = ChatColor.WHITE

        suspend fun merge(from: NBT<Chunk>, into: NBT<Chunk>, ancestor: NBT<Chunk>, mode: MergeMode): NBT<Chunk> {
            val merged = mutableMapOf<AnvilLocation, List<Chunk>>()

            val (new, already) = from.entries.classificationByBoolean { !into.containsKey(it.key) }

            new.forEach { merged[it.key] = it.value }
            already.map { it.key }.forEach { location ->
                Executor.sendTitle("merging region[${location.x}][${location.z}]")

                val mergedChunks = mutableListOf<Chunk>()
                associateChunk(from[location], into[location], ancestor[location])
                    .forEach { associatedMap ->
                        delay(1)

                        Executor.sendTitle("merging region$g[$w${location.x}$g][$w${location.z}$g]$w.chunk$g[$w${associatedMap.key.x}$g][$w${associatedMap.key.z}$g]")

                        val associatedChunks = associatedMap.value
                        val fromC = associatedChunks.from
                        val intoC = associatedChunks.into

                        if (fromC != null && intoC == null) {
                            mergedChunks.add(fromC)
                        } else if (fromC == null && intoC != null) {
                            mergedChunks.add(intoC)
                        } else if (fromC != null && intoC != null) {
                            val anceC = associatedChunks.ancestor ?: when (mode) {
                                MergeMode.KEEP -> fromC
                                MergeMode.REPLACE -> intoC
                            }

                            mergedChunks.add(mergeChunk(fromC, intoC, anceC, mode))
                        }
                    }

                merged[location] = mergedChunks
            }

            Executor.sendTitle("all regions merged")

            return merged
        }

        private fun mergeChunk(
            fromChunk: Chunk,
            intoChunk: Chunk,
            ancestorChunk: Chunk,
            mode: MergeMode
        ): Chunk {
            val resultChunk = Chunk(intoChunk.timestamp, intoChunk.nbt.clone(intoChunk.nbt.name))

            val resultE = mutableListOf<BlockEntity>()
            val intoE = intoChunk.blockEntities
            val fromE = fromChunk.blockEntities
            val anceE = ancestorChunk.blockEntities

            (0 until intoChunk.sections.size).forEach { sectionIndex ->
                val fromS = fromChunk.sections[sectionIndex]
                val intoS = intoChunk.sections[sectionIndex]
                val anceS = ancestorChunk.sections[sectionIndex]

                val fromP = fromS.blockStates.palette
                val fromM = fromS.blockStates.data.unpack(fromP.size).map { fromP[it] }

                val intoP = intoS.blockStates.palette
                val intoM = intoS.blockStates.data.unpack(intoP.size).map { intoP[it] }

                val anceP = anceS.blockStates.palette
                val anceM = anceS.blockStates.data.unpack(anceP.size).map { anceP[it] }

                val resultP = mutableListOf<Palette>()

                (0 until 4096).forEach { block ->
                    val (x, y, z) = coordinate(intoChunk.location, intoS.y, block)

                    val applyIt: (Palette, List<BlockEntity>) -> Palette = apply@{ applyB, applyE ->
                        resultP.add(applyB)
                        applyE.find { it.x == x && it.z == z && it.y == y }
                            ?.also { resultE.add(it) }
                        return@apply applyB
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

                    val anceB = if (anceM.isEmpty()) anceP[0] else anceM[block]
                    val anceBE = anceE.find { it.x == x && it.z == z && it.y == y }

                    val appliedBlock = if (
                        !blockEquals(fromB, fromBE, intoB, intoBE) &&
                        !blockEquals(fromB, fromBE, anceB, anceBE) &&
                        !blockEquals(anceB, anceBE, intoB, intoBE)
                    ) {
                        when (mode) {
                            MergeMode.KEEP -> applyIt(intoB, intoE)
                            MergeMode.REPLACE -> applyIt(fromB, fromE)
                        }
                    } else if (
                        blockEquals(fromB, fromBE, anceB, anceBE) && !blockEquals(fromB, fromBE, intoB, intoBE) ||
                        blockEquals(anceB, anceBE, intoB, intoBE) && !blockEquals(fromB, fromBE, intoB, intoBE)
                    ) {
                        if (fromB == anceB) {
                            applyIt(intoB, intoE)
                        } else {
                            applyIt(fromB, fromE)
                        }
                    } else {
                        applyIt(intoB, intoE)
                    }

                    if (WorldLoader.lightSourceBlocks.contains(appliedBlock.name)) {
                        WorldLoader.registerLightSourceLocation(Triple(x, y, z))
                    }
                }
                val resultPS = resultP.toSet().toList()
                val resultD =
                    if (resultPS.size != 1) resultP.map { resultPS.indexOf(it) }.pack(resultPS.size)
                    else LongArray(0)

                resultChunk.sections[sectionIndex].blockStates.data = resultD
                resultChunk.sections[sectionIndex].blockStates.palette = resultPS
            }

            resultChunk.blockEntities = resultE

            return resultChunk
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

        private fun associateChunk(from: List<Chunk>?, into: List<Chunk>?, ancestor: List<Chunk>?): Map<NBTLocation, AssociatedChunk> {
            val chunkMap = mutableMapOf<NBTLocation, AssociatedChunk>()
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

        private fun coordinate(location: NBTLocation, sectionY: Byte, blockIndex: Int): Triple<Int, Int, Int> {
            val x = location.x
            val y = sectionY.toInt()
            val z = location.z
            return Triple(
                x * 16 + (blockIndex % 16),
                y * 16 + ((blockIndex / (16 * 16)) % 16),
                z * 16 + ((blockIndex / 16) % 16)
            )
        }

        enum class MergeMode {
            KEEP, REPLACE
        }

    }

    private data class AssociatedChunk(var from: Chunk? = null, var into: Chunk? = null, var ancestor: Chunk? = null)

}