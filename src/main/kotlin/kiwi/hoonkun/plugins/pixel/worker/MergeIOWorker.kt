package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.nbt.tag.CompoundTag
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.readFiles
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.readFile
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toAnvil
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toAnvilFormat
import java.io.File

class MergeIOWorker {

    companion object {

        fun repositoryWorldNBTs(
            world: String
        ): WorldAnvil {
            return WorldAnvil(repositoryEntity(world), repositoryPoi(world))
        }

        private inline fun <T: ChunkData> repositoryNBT(
            anvilType: AnvilType,
            world: String,
            generator: (ChunkLocation, Int, CompoundTag) -> T
        ): Anvil<T> {
            val worldPath = anvilType.getRepository(world)
            val anvilFiles = File(worldPath).listFiles() ?: return mapOf()

            return readFiles(anvilFiles).toAnvil(generator)
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

        fun WorldAnvilFormat.writeToClient(world: String) {
            entries.forEach { (type, anvil) ->
                val path = type.getClient(world)
                anvil.entries.forEach { (location, bytes) ->
                    val file = File("$path/r.${location.x}.${location.z}.mca")
                    file.writeBytes(bytes)
                }
            }
        }

        fun copyTerrains(world: String, targetType: MergeWorker.TargetType) {
            File(AnvilType.TERRAIN.getRepository(world)).copyRecursively(File(AnvilType.TERRAIN.getMergeSpace(targetType)).apply { if (!exists()) mkdirs() }, true)
        }

        fun generateTerrainRegistry(): Set<AnvilLocation> {
            val registry = mutableSetOf<AnvilLocation>()
            MergeWorker.TargetType.values().filter { it != MergeWorker.TargetType.RESULT }.forEach { type ->
                val folder = File(AnvilType.TERRAIN.getMergeSpace(type))
                val files = folder.listFiles() ?: throw Exception("merge space not created!")
                registry.addAll(files.map {
                    val segments = it.name.split(".")
                    AnvilLocation(segments[1].toInt(), segments[2].toInt())
                })
            }
            return registry
        }

        fun AnvilLocation.getMergeSpaceTerrains(targetType: MergeWorker.TargetType): List<Terrain>? {
            val file = File("${AnvilType.TERRAIN.getMergeSpace(targetType)}/r.$x.$z.mca")
            return readFile(file)[this]
        }

        fun AnvilLocation.writeTerrains(terrain: List<Terrain>) {
            File(AnvilType.TERRAIN.getMergeSpace(MergeWorker.TargetType.RESULT)).apply { if (!exists()) mkdirs() }
            val file = File("${AnvilType.TERRAIN.getMergeSpace(MergeWorker.TargetType.RESULT)}/r.$x.$z.mca")
            val content = mapOf(this to terrain).toAnvilFormat()[this] ?: return
            file.writeBytes(content)
        }

        fun writeTerrainsToClient(worldName: String) {
            File(AnvilType.TERRAIN.getMergeSpace(MergeWorker.TargetType.RESULT)).copyRecursively(File(AnvilType.TERRAIN.getClient(worldName)), true)
        }

        fun clearMergeSpace() {
            Entry.mergeFolder.deleteRecursively()
        }

    }

}