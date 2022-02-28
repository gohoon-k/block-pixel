package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readVersionedRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toVersionedRegions
import kotlinx.coroutines.delay

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator

import java.io.File

class WriteWorker {

    companion object {

        const val RESULT_OK = "OK"

        private val clientDimensions get() = mapOf(
            "overworld" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_overworld/region",
            "nether" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_nether/DIM-1/region",
            "the_end" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_the_end/DIM1/region"
        )

        suspend fun client2versioned(plugin: Entry, dimensions: List<String>): String {
            dimensions.forEach { dimension ->
                val worldName = "${Entry.levelName}_$dimension"
                val world = plugin.server.getWorld(worldName) ?: return "cannot find world '$worldName'"
                unload(plugin, world)

                val path = clientDimensions[dimension] ?: return "impossible"
                val regions = File(path).listFiles() ?: return "cannot find world file '$worldName'"
                val versioned = ClientRegionFiles(regions).readClientRegions().toVersionedRegions()
                saveVersioned(dimension, versioned)

                load(plugin, world)
            }

            return RESULT_OK
        }

        suspend fun versioned2client(plugin: Entry, dimensions: List<String>): String {
            val versionedPath = Entry.versionedFolder.absolutePath

            dimensions.forEach { dimension ->
                val worldName = "${Entry.levelName}_$dimension"
                val world = plugin.server.getWorld(worldName) ?: return "cannot find world '$worldName'"
                unload(plugin, world)

                val clientDimension = clientDimensions[dimension] ?: return "impossible"
                val versioned = File("$versionedPath/$dimension").listFiles() ?: return "cannot find versioned world file '$dimension'"
                val original = File(clientDimension).listFiles() ?: return "cannot find world file '$worldName'"
                val client = VersionedRegionFiles(versioned)
                    .readVersionedRegions()
                    .toClientRegions(ClientRegionFiles(original))

                replaceClient(clientDimension, client)

                load(plugin, world)
            }

            return RESULT_OK
        }

        private suspend fun unload(plugin: Entry, world: World) {
            var unloaded = false

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.onlinePlayers.filter { it.world.uid == world.uid }.forEach {
                    it.setGravity(false)
                    it.teleport(Location(plugin.void, it.location.x, it.location.y, it.location.z))
                }
                world.isAutoSave = false
                world.save()

                do {
                    unloaded = plugin.server.unloadWorld(world, true)
                } while (!unloaded)
            })

            while (!unloaded) { delay(100) }
        }

        private suspend fun load(plugin: Entry, world: World) {
            var loaded = false

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.createWorld(WorldCreator(world.name))!!.also { created ->
                    plugin.server.onlinePlayers.filter { it.world.uid == plugin.void.uid }.forEach {
                        it.teleport(Location(created, it.location.x, it.location.y, it.location.z))
                        it.setGravity(true)
                    }
                    created.isAutoSave = true
                    created.loadedChunks.forEach { chunk ->
                        chunk.unload()
                        chunk.load()
                    }
                    loaded = true
                }
            })

            while (!loaded) { delay(100) }
        }

        private fun saveVersioned(dimension: String, versioned: VersionedRegions) {
            versioned.get.entries.forEach { (location, region) ->
                val outputDirectory = File("${Entry.versionedFolder.absolutePath}/$dimension")
                if (!outputDirectory.exists()) outputDirectory.mkdirs()

                val outputDataFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca.d")
                val outputTypesFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca.t")

                outputDataFile.writeBytes(region.data.toByteArray())
                outputTypesFile.writeBytes(region.types.toByteArray())
            }
        }

        private fun replaceClient(path: String, regions: ClientRegions) {
            regions.get.entries.forEach { (location, bytes) ->
                val outputDirectory = File(path)

                val outputFile = File("${outputDirectory.absolutePath}/r.${location.x}.${location.z}.mca")

                outputFile.writeBytes(bytes)
            }
        }

    }

}