package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kiwi.hoonkun.plugins.pixel.nbt.Tag
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.worker.WorldLightUpdater.Companion.isEmittingLights
import kiwi.hoonkun.plugins.pixel.worker.ArrayPacker.Companion.pack
import kiwi.hoonkun.plugins.pixel.worker.ArrayPacker.Companion.unpack
import kiwi.hoonkun.plugins.pixel.worker.MergeIOWorker.Companion.getMergeSpaceTerrains
import kiwi.hoonkun.plugins.pixel.worker.MergeIOWorker.Companion.writeTerrains
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
            val terrainRegistry = MergeIOWorker.generateTerrainRegistry()

            Tag.addIgnorePath("sections[*].SkyLight")

            terrainRegistry.forEach { location ->
                val mergedTerrains = mutableListOf<Terrain>()

                Executor.sendTitle("reading terrain, region$g[$w${location.x}$g][$w${location.z}$g]$w")

                val fromTerrains = location.getMergeSpaceTerrains(TargetType.SOURCE)
                val intoTerrains = location.getMergeSpaceTerrains(TargetType.CURRENT)

                if (intoTerrains == null && fromTerrains != null) {
                    mergedTerrains.addAll(fromTerrains)
                    return@forEach
                }

                if (intoTerrains != null && fromTerrains == null) {
                    mergedTerrains.addAll(intoTerrains)
                    return@forEach
                }

                if (fromTerrains == intoTerrains && intoTerrains != null) {
                    mergedTerrains.addAll(intoTerrains)
                    return@forEach
                }

                val ancestorTerrains = location.getMergeSpaceTerrains(TargetType.BASE)

                associateTerrain(
                    fromTerrains,
                    intoTerrains,
                    ancestorTerrains
                ).forEach { associatedMap ->
                    throwIfInactive(job)

                    val relative = associatedMap.key.toRelative(location)
                    Executor.sendTitle("merging terrain, region$g[$w${location.x}$g][$w${location.z}$g]$w.chunk$g[$w${relative.x}$g][$w${relative.z}$g]")

                    val associatedTerrains = associatedMap.value
                    val fromT = associatedTerrains.from
                    val intoT = associatedTerrains.into

                    if (fromT != null && intoT == null) {
                        mergedTerrains.add(fromT)
                    } else if ((fromT == null && intoT != null) || (fromT != null && intoT != null && fromT == intoT)) {
                        mergedTerrains.add(intoT)
                    } else if (fromT != null && intoT != null) {
                        val anceT = associatedTerrains.ancestor ?: when (mode) {
                            MergeMode.KEEP -> fromT
                            MergeMode.REPLACE -> intoT
                        }

                        mergedTerrains.add(mergeTerrain(fromT, intoT, anceT, mode))
                    }
                }

                location.writeTerrains(mergedTerrains)
            }

            Tag.removeIgnorePath("sections[*].SkyLight")

            val allMergedEntities = mutableListOf<EntityEach>()

            val fEntities = from.entity.values.flatten().flatMap { it.entities }
            val iEntities = into.entity.values.flatten().flatMap { it.entities }
            val oEntities = ancestor.entity.values.flatten().flatMap { it.entities }

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

            val villagers = allMergedEntities.filter { it.id == "minecraft:villager" }
            val fVillagers = fEntities.filter { it.id == "minecraft:villager" }
            val iVillagers = iEntities.filter { it.id == "minecraft:villager" }

            val mergedMutableEntityAnvil: MutableAnvil<MutableEntity> = mutableMapOf()

            allMergedEntities.forEachIndexed { index, entityEach ->
                throwIfInactive(job)

                Executor.sendTitle("classifying entities to each chunks [$index/${allMergedEntities.size}]")

                val nbtLocation = ChunkLocation(
                    floor(entityEach.pos[0] / 16.0).toInt(),
                    floor(entityEach.pos[2] / 16.0).toInt()
                )

                val anvilLocation = AnvilLocation(
                    floor(entityEach.pos[0] / 16.0 / 32.0).toInt(),
                    floor(entityEach.pos[2] / 16.0 / 32.0).toInt()
                )

                if (!mergedMutableEntityAnvil.containsKey(anvilLocation))
                    mergedMutableEntityAnvil[anvilLocation] = mutableListOf()

                var entity = mergedMutableEntityAnvil[anvilLocation]!!.find { it.location == nbtLocation }
                if (entity == null) {
                    val oldEntity = from.entity[anvilLocation]!!.find { it.location == nbtLocation }
                        ?: into.entity[anvilLocation]!!.find { it.location == nbtLocation }
                        ?: throw Exception("cannot find entity from 'into' and 'from'.")

                    entity = MutableEntity(nbtLocation, oldEntity.timestamp, CompoundTag(mutableMapOf(), null, null))
                        .apply {
                            position = intArrayOf(nbtLocation.x, nbtLocation.z)
                            dataVersion = oldEntity.dataVersion
                            entities = mutableListOf()
                        }
                    mergedMutableEntityAnvil[anvilLocation]!!.add(entity)
                }

                entity.entities!!.add(entityEach)
            }

            val mergedEntityAnvil: Anvil<Entity> = mergedMutableEntityAnvil.entries.associate {
                it.key to it.value.map { mutableEntity -> mutableEntity.toEntity() }
            }

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

            val mergedMutablePoiAnvil: MutableAnvil<MutablePoi> = mutableMapOf()

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

                if (!mergedMutablePoiAnvil.containsKey(anvilLocation))
                    mergedMutablePoiAnvil[anvilLocation] = mutableListOf()

                var poi = mergedMutablePoiAnvil[anvilLocation]!!.find { poi -> nbtLocation == poi.location }

                if (poi == null) {
                    val timestamp = from.poi[anvilLocation]!!.find { nbtLocation == it.location }?.timestamp
                        ?: into.poi[anvilLocation]!!.find { nbtLocation == it.location }?.timestamp
                        ?: throw Exception("cannot find poi in 'from' and 'into'.")

                    poi = MutablePoi(nbtLocation, timestamp, CompoundTag(mutableMapOf(), null, null))
                    mergedMutablePoiAnvil[anvilLocation]!!.add(poi)
                }

                if (poi.sections == null)
                    poi.sections = MutablePoiSections(CompoundTag(mutableMapOf(), "Sections", poi.nbt))

                if (poi.dataVersion == null)
                    poi.dataVersion = from.poi[anvilLocation]!!.find { nbtLocation == it.location }?.dataVersion
                        ?: into.poi[anvilLocation]!!.find { nbtLocation == it.location }?.dataVersion
                        ?: throw Exception("cannot find poi in 'from' and 'into'.")

                if (poi.sections!![sectionY] == null)
                    poi.sections!![sectionY] = MutablePoiSection(CompoundTag(mutableMapOf(), sectionY.toString(), poi.sections!!.nbt))
                        .apply { valid = 1 }

                if (poi.sections!![sectionY]!!.records == null)
                    poi.sections!![sectionY]!!.records = mutableListOf()

                poi.sections!![sectionY]!!.records!!.add(record)
            }

            val mergedPoiAnvil: Anvil<Poi> = mergedMutablePoiAnvil.entries.associate {
                it.key to it.value.map { mutablePoi -> mutablePoi.toPoi() }
            }

            return WorldAnvil(mergedEntityAnvil, mergedPoiAnvil)
        }

        private fun mergeTerrain(
            fromTerrain: Terrain,
            intoTerrain: Terrain,
            ancestorTerrain: Terrain,
            mode: MergeMode
        ): Terrain {
            val resultTerrain = Terrain(intoTerrain.timestamp, intoTerrain.nbt.clone(intoTerrain.nbt.name))

            val resultE = mutableListOf<BlockEntity>()
            val intoE = intoTerrain.blockEntities
            val fromE = fromTerrain.blockEntities
            val anceE = ancestorTerrain.blockEntities

            (0 until intoTerrain.sections.size).forEach { sectionIndex ->
                val fromS = fromTerrain.sections[sectionIndex]
                val intoS = intoTerrain.sections[sectionIndex]

                if (fromS == intoS) return@forEach

                val anceS = ancestorTerrain.sections[sectionIndex]

                val fromP = fromS.blockStates.palette
                val fromM = fromS.blockStates.data.unpack(fromP.size).map { fromP[it] }

                val intoP = intoS.blockStates.palette
                val intoM = intoS.blockStates.data.unpack(intoP.size).map { intoP[it] }

                val anceP = anceS.blockStates.palette
                val anceM = anceS.blockStates.data.unpack(anceP.size).map { anceP[it] }

                val resultP = mutableListOf<Palette>()

                (0 until 4096).forEach { block ->
                    val (x, y, z) = coordinate(intoTerrain.location, intoS.y, block)

                    val applyIt: (Palette, List<BlockEntity>) -> Palette = apply@ { applyB, applyE ->
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

                    var appliedFromSource = false
                    val appliedBlock = if (
                        !blockEquals(fromB, fromBE, intoB, intoBE) &&
                        !blockEquals(fromB, fromBE, anceB, anceBE) &&
                        !blockEquals(anceB, anceBE, intoB, intoBE)
                    ) {
                        appliedFromSource = mode == MergeMode.REPLACE
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
                            appliedFromSource = true
                            applyIt(fromB, fromE)
                        }
                    } else {
                        applyIt(intoB, intoE)
                    }

                    if (appliedFromSource && appliedBlock.isEmittingLights()) {
                        WorldLightUpdater.addTarget(Triple(x, y, z))
                    }
                }
                val resultPS = resultP.toSet().toList()
                val resultD =
                    if (resultPS.size != 1) resultP.map { resultPS.indexOf(it) }.pack(resultPS.size)
                    else LongArray(0)

                resultTerrain.sections[sectionIndex].blockStates.data = resultD
                resultTerrain.sections[sectionIndex].blockStates.palette = resultPS
            }

            resultTerrain.blockEntities = resultE

            return resultTerrain
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
            return poi.values.asSequence()
                .flatten()
                .flatMap { it.sections.values }
                .flatMap { it.records }
                .toList()
        }

        private fun associateTerrain(from: List<Terrain>?, into: List<Terrain>?, ancestor: List<Terrain>?): Map<ChunkLocation, AssociatedChunk> {
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

    }

    enum class MergeMode {
        KEEP, REPLACE
    }

    enum class TargetType(val path: String) {
        CURRENT("current"), SOURCE("source"), BASE("base"), RESULT("result")
    }

    private data class AssociatedChunk(var from: Terrain? = null, var into: Terrain? = null, var ancestor: Terrain? = null)

}