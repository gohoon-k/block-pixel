package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.worker.WorldLightUpdater.Companion.isEmittingLights
import kiwi.hoonkun.plugins.pixel.worker.ArrayPacker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.ArrayPacker.Companion.unpack
import kotlinx.coroutines.Job

import org.bukkit.ChatColor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor


class MergeWorker {

    companion object {

        private val g = ChatColor.GRAY
        private val w = ChatColor.WHITE

        private val maxFreeTickets = mapOf(
            "minecraft:meeting" to 32,
            "minecraft:home" to 1,
            "minecraft:armorer" to 1,
            "minecraft:butcher" to 1,
            "minecraft:cartographer" to 1,
            "minecraft:cleric" to 1,
            "minecraft:farmer" to 1,
            "minecraft:fisherman" to 1,
            "minecraft:fletcher" to 1,
            "minecraft:leatherworker" to 1,
            "minecraft:librarian" to 1,
            "minecraft:mason" to 1,
            "minecraft:shepherd" to 1,
            "minecraft:toolsmith" to 1,
            "minecraft:weaponsmith" to 1,
        )

        private fun throwIfInactive(job: Job?) {
            if (job?.isActive != true) throw CancellationException()
        }

        fun merge(job: Job?, from: WorldAnvil, into: WorldAnvil, ancestor: WorldAnvil, mode: MergeMode): WorldAnvil {
            val mergedChunk: MutableAnvil<Terrain> = mutableMapOf()

            Executor.sendTitle("merging chunks...")

            val (newChunk, existsChunk) =
                from.terrain.entries.classificationByBoolean { !into.terrain.containsKey(it.key) }

            newChunk.forEach { mergedChunk[it.key] = it.value.toMutableList() }

            existsChunk.map { it.key }.forEach { location ->
                Executor.sendTitle("merging region[${location.x}][${location.z}]")

                val mergedChunks = mutableListOf<Terrain>()
                associateChunk(from.terrain[location], into.terrain[location], ancestor.terrain[location])
                    .forEach { associatedMap ->
                        throwIfInactive(job)

                        Executor.sendTitle("merging terrain, region$g[$w${location.x}$g][$w${location.z}$g]$w.chunk$g[$w${associatedMap.key.x}$g][$w${associatedMap.key.z}$g]")

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

                mergedChunk[location] = mergedChunks
            }

            Executor.sendTitle("merging entities...")

            val allMergedEntities = mutableListOf<EntityEach>()

            val fEntities = from.entity.values.flatten().map { it.entities }.flatten()
            val iEntities = into.entity.values.flatten().map { it.entities }.flatten()
            val oEntities = ancestor.entity.values.flatten().map { it.entities }.flatten()

            val aEntities = setOf(
                *fEntities.toTypedArray(),
                *iEntities.toTypedArray(),
                *oEntities.toTypedArray()
            ).toList()

            aEntities.forEachIndexed { index, entity ->
                throwIfInactive(job)

                Executor.sendTitle("merging entities, [$index/${aEntities.size}]")

                val oHasE = oEntities.contains(entity)
                val iHasE = iEntities.contains(entity)
                val fHasE = fEntities.contains(entity)

                val findE: (searchFrom: List<EntityEach>) -> EntityEach = { searchFrom ->
                    searchFrom.find { entity.uuid.contentEquals(it.uuid) }!!
                }

                allMergedEntities.add(
                    select(mode, iEntities, fEntities, oHasE, iHasE, fHasE, findE) ?: return@forEachIndexed
                )
            }

            Executor.sendTitle("classifying entities to regions...")

            val villagers = allMergedEntities.filter { it.id == "minecraft:villager" }
            val fVillagers = fEntities.filter { it.id == "minecraft:villager" }
            val iVillagers = iEntities.filter { it.id == "minecraft:villager" }

            val mergedMutableEntity: MutableAnvil<MutableEntity> = mutableMapOf()

            allMergedEntities.forEach { entityEach ->
                throwIfInactive(job)

                val nbtLocation = ChunkLocation(
                    floor(entityEach.pos[0] / 16.0).toInt(),
                    floor(entityEach.pos[2] / 16.0).toInt()
                )

                val anvilLocation = AnvilLocation(
                    floor(entityEach.pos[0] / 16.0 / 32.0).toInt(),
                    floor(entityEach.pos[2] / 16.0 / 32.0).toInt()
                )

                if (!mergedMutableEntity.containsKey(anvilLocation))
                    mergedMutableEntity[anvilLocation] = mutableListOf()

                var entity = mergedMutableEntity[anvilLocation]!!.find { it.location == nbtLocation }
                if (entity == null) {
                    val oldEntity = from.entity[anvilLocation]!!.find { it.location == nbtLocation }
                        ?: into.entity[anvilLocation]!!.find { it.location == nbtLocation }
                        ?: throw Exception("cannot find entity from 'into' and 'from'.")

                    entity = MutableEntity(nbtLocation, oldEntity.timestamp, CompoundTag(mutableMapOf(), null))
                        .apply {
                            position = intArrayOf(nbtLocation.x, nbtLocation.z)
                            dataVersion = oldEntity.dataVersion
                            entities = mutableListOf()
                        }
                    mergedMutableEntity[anvilLocation]!!.add(entity)
                }

                entity.entities!!.add(entityEach)
            }

            val mergedEntity: Anvil<Entity> = mergedMutableEntity.entries.associate {
                it.key to it.value.map { mutableEntity -> mutableEntity.toEntity() }
            }

            Executor.sendTitle("merging poi...")

            val fRecords = flattenRecords(from.poi)
            val iRecords = flattenRecords(into.poi)
            val oRecords = flattenRecords(ancestor.poi)

            val aRecords = setOf(
                *fRecords.toTypedArray(),
                *iRecords.toTypedArray(),
                *oRecords.toTypedArray()
            ).toList()
            
            val allMergedRecords = mutableListOf<PoiRecord>()
            
            aRecords.forEachIndexed { index, record ->
                throwIfInactive(job)

                Executor.sendTitle("merging poi records, [$index/${aRecords.size}]")

                val oHasR = oRecords.contains(record)
                val iHasR = iRecords.contains(record)
                val fHasR = fRecords.contains(record)

                val findE: (searchFrom: List<PoiRecord>) -> PoiRecord = { searchFrom ->
                    searchFrom.find { record.pos.contentEquals(it.pos) }!!
                }

                allMergedRecords.add(
                    select(mode, iRecords, fRecords, oHasR, iHasR, fHasR, findE) ?: return@forEachIndexed
                )
            }

            val jobSites = villagers.mapNotNull { it.brain?.memories?.jobSite }
            val meetingPoints = villagers.mapNotNull { it.brain?.memories?.meetingPoint }
            val homes = villagers.mapNotNull { it.brain?.memories?.home }

            val poiBlocks = mutableListOf<EntityMemoryValue>()
            poiBlocks.addAll(jobSites)
            poiBlocks.addAll(meetingPoints)
            poiBlocks.addAll(homes)

            val findVillager: (PoiRecord, EntityEach) -> Boolean = { poiRecord, entityEach ->
                if (poiRecord.type == "minecraft:home") {
                    entityEach.brain?.memories?.home?.pos?.contentEquals(poiRecord.pos) == true
                } else if (maxFreeTickets.containsKey(poiRecord.type)) {
                    entityEach.brain?.memories?.jobSite?.pos?.contentEquals(poiRecord.pos) == true
                } else {
                    false
                }
            }

            val resetMemory: (PoiRecord, EntityEach?) -> Unit = { poiRecord, entityEach ->
                if (poiRecord.type == "minecraft:home") {
                    entityEach?.brain?.memories?.home = null
                } else if (maxFreeTickets.containsKey(poiRecord.type)) {
                    entityEach?.brain?.memories?.jobSite = null
                }
            }

            allMergedRecords.forEachIndexed { index, poiRecord ->
                throwIfInactive(job)

                Executor.sendTitle("applying free tickets of poi records [$index/${allMergedRecords.size}]")

                if (!maxFreeTickets.keys.contains(poiRecord.type)) return@forEachIndexed

                var newFreeTickets = (maxFreeTickets[poiRecord.type] ?: 0) - poiBlocks.count { it.pos.contentEquals(poiRecord.pos) }
                if (poiRecord.type != "minecraft:meeting" && maxFreeTickets.containsKey(poiRecord.type) && newFreeTickets < 0) {
                    when (mode) {
                        MergeMode.KEEP -> {
                            resetMemory(poiRecord, fVillagers.find { findVillager(poiRecord, it) })
                        }
                        MergeMode.REPLACE -> {
                            resetMemory(poiRecord, iVillagers.find { findVillager(poiRecord, it) })
                        }
                    }
                    newFreeTickets = 0
                } else if (poiRecord.type == "minecraft:meeting") {
                    newFreeTickets = 32
                    villagers.forEach { it.brain?.memories?.meetingPoint = null }
                }
                poiRecord.freeTickets = newFreeTickets
            }

            val mergedMutablePoi: MutableAnvil<MutablePoi> = mutableMapOf()

            allMergedRecords.forEachIndexed { index, record ->
                throwIfInactive(job)

                Executor.sendTitle("classifying poi records [$index/${allMergedRecords.size}]")

                val nbtLocation = ChunkLocation(
                    floor(record.pos[0] / 16.0).toInt(),
                    floor(record.pos[2] / 16.0).toInt()
                )

                val anvilLocation = AnvilLocation(
                    floor(record.pos[0] / 16.0 / 32.0).toInt(),
                    floor(record.pos[2] / 16.0 / 32.0).toInt()
                )

                val sectionY = floor(record.pos[1] / 16.0).toInt()

                if (!mergedMutablePoi.containsKey(anvilLocation))
                    mergedMutablePoi[anvilLocation] = mutableListOf()

                var poi = mergedMutablePoi[anvilLocation]!!.find { poi -> nbtLocation == poi.location }

                if (poi == null) {
                    val timestamp = from.poi[anvilLocation]!!.find { nbtLocation == it.location }?.timestamp
                        ?: into.poi[anvilLocation]!!.find { nbtLocation == it.location }?.timestamp
                        ?: throw Exception("cannot find poi in 'from' and 'into'.")

                    poi = MutablePoi(nbtLocation, timestamp, CompoundTag(mutableMapOf(), null))
                    mergedMutablePoi[anvilLocation]!!.add(poi)
                }

                if (poi.sections == null)
                    poi.sections = mutableMapOf()

                if (poi.dataVersion == null)
                    poi.dataVersion = from.poi[anvilLocation]!!.find { nbtLocation == it.location }?.dataVersion
                        ?: into.poi[anvilLocation]!!.find { nbtLocation == it.location }?.dataVersion
                        ?: throw Exception("cannot find poi in 'from' and 'into'.")

                if (poi.sections!![sectionY] == null)
                    poi.sections!![sectionY] = MutablePoiSection(CompoundTag(mutableMapOf(), sectionY.toString()))
                        .apply { valid = 1 }

                if (poi.sections!![sectionY]!!.records == null)
                    poi.sections!![sectionY]!!.records = mutableListOf()

                poi.sections!![sectionY]!!.records!!.add(record)
            }

            val mergedPoi: Anvil<Poi> = mergedMutablePoi.entries.associate {
                it.key to it.value.map { mutablePoi -> mutablePoi.toPoi() }
            }

            return WorldAnvil(mergedChunk, mergedEntity, mergedPoi)
        }

        private fun mergeChunk(
            fromChunk: Terrain,
            intoChunk: Terrain,
            ancestorChunk: Terrain,
            mode: MergeMode
        ): Terrain {
            val resultChunk = Terrain(intoChunk.timestamp, intoChunk.nbt.clone(intoChunk.nbt.name))

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

                    if (appliedBlock.isEmittingLights()) {
                        WorldLightUpdater.addTarget(Triple(x, y, z))
                    }
                }
                val resultPS = resultP.toSet().toList()
                val resultD =
                    if (resultPS.size != 1) resultP.map { resultPS.indexOf(it) }.pack(resultPS.size)
                    else LongArray(0)

                resultChunk.sections[sectionIndex].blockStates.data = resultD
                resultChunk.sections[sectionIndex].blockStates.palette = resultPS
                resultChunk.sections[sectionIndex].skyLight = null
            }

            resultChunk.blockEntities = resultE

            return resultChunk
        }

        private fun <T> select(
            mode: MergeMode,
            into: List<T>,
            from: List<T>,
            oHas: Boolean,
            iHas: Boolean,
            fHas: Boolean,
            findE: (searchFrom: List<T>) -> T
        ): T? {
            
            if (oHas && iHas && !fHas) {
                if (mode == MergeMode.KEEP) return findE(into)
            } else if (oHas && !iHas && fHas) {
                if (mode == MergeMode.REPLACE) return findE(from)
            } else if (!oHas && iHas && !fHas) {
                return findE(into)
            } else if (!oHas && !iHas && fHas) {
                return findE(from)
            } else if (iHas) { // fHasE is always true, in this block.
                return when (mode) {
                    MergeMode.KEEP -> findE(into)
                    MergeMode.REPLACE -> findE(from)
                }
            }

            return null
        }

        private fun flattenRecords(poi: Anvil<Poi>): List<PoiRecord> {
            return poi.values.flatten()
                .map { it.sections.values.toTypedArray() }
                .toTypedArray()
                .flatten()
                .map { it.records.toTypedArray() }
                .toTypedArray()
                .flatten()
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

        private fun associateChunk(from: List<Terrain>?, into: List<Terrain>?, ancestor: List<Terrain>?): Map<ChunkLocation, AssociatedChunk> {
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

        enum class MergeMode {
            KEEP, REPLACE
        }

    }

    private data class AssociatedChunk(var from: Terrain? = null, var into: Terrain? = null, var ancestor: Terrain? = null)

}