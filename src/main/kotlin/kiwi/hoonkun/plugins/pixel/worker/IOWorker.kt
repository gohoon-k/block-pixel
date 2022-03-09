package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.read
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toAnvil

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


        fun repositoryWorldNBTs(
            dimensions: List<String>
        ): Map<String, WorldAnvil> {
            return dimensions.associateWith { WorldAnvil(repositoryChunk(it), repositoryEntity(it), repositoryPoi(it)) }
        }

        private inline fun <T: ChunkData> repositoryNBT(
            anvilType: AnvilType,
            dimension: String,
            generator: (ChunkLocation, Int, CompoundTag) -> T
        ): Anvil<T> {
            val dimensionPath = getVersionedPath(anvilType, dimension)
            val anvilFiles = File(dimensionPath).listFiles()

            return anvilFiles?.read()?.toAnvil(generator) ?: mapOf()
        }

        private fun repositoryChunk(dimension: String): Anvil<Terrain> {
            return repositoryNBT(
                AnvilType.TERRAIN,
                dimension
            ) { _, timestamp, nbt -> Terrain(timestamp, nbt) }
        }

        private fun repositoryPoi(dimension: String): Anvil<Poi> {
            return repositoryNBT(
                AnvilType.POI,
                dimension
            ) { location, timestamp, nbt -> Poi(location, timestamp, nbt) }
        }

        private fun repositoryEntity(dimension: String): Anvil<Entity> {
            return repositoryNBT(
                AnvilType.ENTITY,
                dimension
            ) { _, timestamp, nbt -> Entity(timestamp, nbt) }
        }

        fun writeWorldAnvilToClient(regionAnvil: WorldAnvilFormat, dimension: String) {
            regionAnvil.entries.forEach { (type, anvil) ->
                val path = getClientPath(type, dimension)
                anvil.entries.forEach { (location, bytes) ->
                    val file = File("$path/r.${location.x}.${location.z}.mca")
                    file.writeBytes(bytes)
                }
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