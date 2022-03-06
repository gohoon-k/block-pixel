package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.read
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toNBT

import java.io.File

class IOWorker {

    companion object {

        private val clientDimDir = mapOf(
            "overworld" to "",
            "nether" to "/DIM-1",
            "the_end" to "/DIM1"
        )

        private fun getClientPath(anvilType: AnvilType, dimension: String) =
            "${Entry.clientFolder.absolutePath}/${Entry.levelName}_${dimension}${clientDimDir[dimension]}/${anvilType.path}"

        private fun getVersionedPath(anvilType: AnvilType, dimension: String) =
            "${Entry.versionedFolder.absolutePath}/${dimension}/${anvilType.path}"

        private inline fun <T: NBTData> readVersionedAnvils(
            anvilType: AnvilType,
            dimensions: List<String>,
            generator: (NBTLocation, Int, CompoundTag) -> T
        ): List<NBT<T>> {
            val result = mutableListOf<NBT<T>>()
            dimensions.forEach { dimension ->
                val dimensionPath = getVersionedPath(anvilType, dimension)
                val anvilFiles = File(dimensionPath).listFiles()
                if (anvilFiles == null) result.add(mapOf())
                else result.add(anvilFiles.read().toNBT(generator))
            }
            return result
        }

        fun readVersionedRegionAnvils(dimensions: List<String>): List<NBT<Chunk>> {
            return readVersionedAnvils(
                AnvilType.REGION,
                dimensions
            ) { _, timestamp, nbt -> Chunk(timestamp, nbt) }
        }

        fun readVersionedPoiAnvils(dimensions: List<String>): List<NBT<Poi>> {
            return readVersionedAnvils(
                AnvilType.POI,
                dimensions
            ) { location, timestamp, nbt -> Poi(location, timestamp, nbt) }
        }

        fun readVersionedEntityAnvils(dimensions: List<String>): List<NBT<Entity>> {
            return readVersionedAnvils(
                AnvilType.ENTITY,
                dimensions
            ) { _, timestamp, nbt -> Entity(timestamp, nbt) }
        }

        fun writeRegionAnvilToClient(regionAnvil: Anvils, dimension: String) {
            val path = getClientPath(AnvilType.REGION, dimension)
            regionAnvil.entries.forEach { (location, bytes) ->
                val file = File("$path/r.${location.x}.${location.z}.mca")
                file.writeBytes(bytes)
            }
        }

        suspend fun addToVersionControl(
            plugin: Entry,
            dimensions: List<String>,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            dimensions.forEach { dimension ->
                copyAnvilFiles(plugin, dimension,false, needsUnload, needsLoad)
            }
        }

        suspend fun replaceFromVersionControl(
            plugin: Entry,
            dimensions: List<String>,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            dimensions.forEach { dimension ->
                copyAnvilFiles(plugin, dimension, true, needsUnload, needsLoad)
            }
        }

        private suspend fun copyAnvilFiles(
            plugin: Entry,
            dimension: String,
            replace: Boolean,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            if (needsUnload) {
                WorldLoader.movePlayersTo(plugin, dimension)
                WorldLoader.unload(plugin, dimension)
            }

            AnvilType.values().forEach { anvilType ->

                val fromPath = if (!replace) getClientPath(anvilType, dimension) else getVersionedPath(anvilType, dimension)
                val toPath = if (!replace) getVersionedPath(anvilType, dimension) else getClientPath(anvilType, dimension)

                val fromDirectory = File(fromPath)
                val toDirectory = File(toPath)

                if (!fromDirectory.exists()) fromDirectory.mkdirs()
                if (!toDirectory.exists()) toDirectory.mkdirs()

                val fromFiles = fromDirectory.listFiles() ?: throw Exception("cannot find directory of anvils with dimension '$dimension'")
                fromFiles.forEach { file -> file.copyTo(File("${toDirectory.absolutePath}/${file.name}"), true) }

            }

            if (needsLoad) {
                WorldLoader.load(plugin, dimension)
                WorldLoader.returnPlayersTo(plugin, dimension)
            }
        }

    }

}