package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.read
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toAnvil

import java.io.File

class IOWorker {

    companion object {

        private fun getClientPath(anvilType: AnvilType, worldName: String): String {
            val worldDir = "${Entry.clientFolder.absolutePath}/$worldName"
            val dimDir = File(worldDir).listFiles()?.find { it.name.contains("DIM") }

            return if (dimDir == null) "$worldDir/${anvilType.path}"
            else "$worldDir/${dimDir.name}/${anvilType.path}"
        }

        private fun getVersionedPath(anvilType: AnvilType, worldName: String) =
            "${Entry.versionedFolder.absolutePath}/${worldName}/${anvilType.path}"


        fun repositoryWorldNBTs(
            world: String
        ): WorldAnvil {
            return WorldAnvil(repositoryChunk(world), repositoryEntity(world), repositoryPoi(world))
        }

        private inline fun <T: ChunkData> repositoryNBT(
            anvilType: AnvilType,
            world: String,
            generator: (ChunkLocation, Int, CompoundTag) -> T
        ): Anvil<T> {
            val worldPath = getVersionedPath(anvilType, world)
            val anvilFiles = File(worldPath).listFiles()

            return anvilFiles?.read()?.toAnvil(generator) ?: mapOf()
        }

        private fun repositoryChunk(world: String): Anvil<Terrain> {
            return repositoryNBT(
                AnvilType.TERRAIN,
                world
            ) { _, timestamp, nbt -> Terrain(timestamp, nbt) }
        }

        private fun repositoryPoi(world: String): Anvil<Poi> {
            return repositoryNBT(
                AnvilType.POI,
                world
            ) { location, timestamp, nbt -> Poi(location, timestamp, nbt) }
        }

        private fun repositoryEntity(world: String): Anvil<Entity> {
            return repositoryNBT(
                AnvilType.ENTITY,
                world
            ) { _, timestamp, nbt -> Entity(timestamp, nbt) }
        }

        fun writeWorldAnvilToClient(regionAnvil: WorldAnvilFormat, world: String) {
            regionAnvil.entries.forEach { (type, anvil) ->
                val path = getClientPath(type, world)
                anvil.entries.forEach { (location, bytes) ->
                    val file = File("$path/r.${location.x}.${location.z}.mca")
                    file.writeBytes(bytes)
                }
            }
        }

        suspend fun addToVersionControl(
            plugin: Entry,
            world: String,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            copyAnvilFiles(plugin, world,false, needsUnload, needsLoad)
        }

        suspend fun replaceFromVersionControl(
            plugin: Entry,
            world: String,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            copyAnvilFiles(plugin, world, true, needsUnload, needsLoad)
        }

        private suspend fun copyAnvilFiles(
            plugin: Entry,
            world: String,
            replace: Boolean,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            if (needsUnload) {
                WorldLoader.movePlayersTo(plugin, world)
                WorldLoader.unload(plugin, world)
            }

            AnvilType.values().forEach { anvilType ->

                val fromPath = if (!replace) getClientPath(anvilType, world) else getVersionedPath(anvilType, world)
                val toPath = if (!replace) getVersionedPath(anvilType, world) else getClientPath(anvilType, world)

                val fromDirectory = File(fromPath)
                val toDirectory = File(toPath)

                if (!fromDirectory.exists()) fromDirectory.mkdirs()
                if (!toDirectory.exists()) toDirectory.mkdirs()

                val fromFiles = fromDirectory.listFiles() ?: throw Exception("cannot find directory of anvils with world '$world'")
                fromFiles.forEach { file -> file.copyTo(File("${toDirectory.absolutePath}/${file.name}"), true) }

            }

            if (needsLoad) {
                WorldLoader.load(plugin, world)
                WorldLoader.returnPlayersTo(plugin, world)
            }
        }

    }

}