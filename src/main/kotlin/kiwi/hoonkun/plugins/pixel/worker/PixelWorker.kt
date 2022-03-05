package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.ClientRegionFiles
import kiwi.hoonkun.plugins.pixel.ClientRegions
import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.Regions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.readClientRegions
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toRegions
import java.io.File

class PixelWorker {

    companion object {

        private val clientDimensions get() = mapOf(
            "overworld" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_overworld/region",
            "nether" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_nether/DIM-1/region",
            "the_end" to "${Entry.clientFolder.absolutePath}/${Entry.levelName}_the_end/DIM1/region"
        )

        private val versionedDimension get() = mapOf(
            "overworld" to "${Entry.versionedFolder.absolutePath}/overworld",
            "nether" to "${Entry.versionedFolder.absolutePath}/nether",
            "the_end" to "${Entry.versionedFolder.absolutePath}/the_end"
        )

        fun read(dimensions: List<String>): List<Regions> {
            val result = mutableListOf<Regions>()
            dimensions.forEach { dimension ->
                val dimensionPath = versionedDimension[dimension] ?: throw Exception("invalid dimension")
                val files = File(dimensionPath).listFiles() ?: return@forEach
                result.add(ClientRegionFiles(files).readClientRegions().toRegions())
            }
            return result
        }

        suspend fun ClientRegions.writeToClient(plugin: Entry, dimension: String) {
            val path = clientDimensions[dimension] ?: throw Exception("invalid dimension")
            WorldLoader.unload(plugin, dimension)
            get.entries.forEach { (location, bytes) ->
                val file = File("$path/r.${location.x}.${location.z}.mca")
                file.writeBytes(bytes)
            }
            WorldLoader.load(plugin, dimension)
        }

        suspend fun addToVersionControl(
            plugin: Entry,
            dimensions: List<String>,
            unload: Boolean = true,
            movePlayer: Boolean = true,
            reload: Boolean = true,
            returnPlayer: Boolean = true
        ) {
            dimensions.forEach { dimension ->
                copyRegions(plugin, dimension, false, unload, movePlayer, reload, returnPlayer)
            }
        }

        suspend fun replaceFromVersionControl(
            plugin: Entry,
            dimensions: List<String>,
            unload: Boolean = true,
            movePlayer: Boolean = true,
            reload: Boolean = true,
            returnPlayer: Boolean = true
        ) {
            dimensions.forEach { dimension ->
                copyRegions(plugin, dimension, true, unload, movePlayer, reload, returnPlayer)
            }
        }

        private suspend fun copyRegions(
            plugin: Entry,
            dimension: String,
            replace: Boolean,
            unload: Boolean = true,
            movePlayer: Boolean = true,
            reload: Boolean = true,
            returnPlayer: Boolean = true
        ) {
            if (movePlayer) WorldLoader.movePlayersTo(plugin, dimension)
            if (unload) WorldLoader.unload(plugin, dimension)

            val fromPath = (if (!replace) clientDimensions else versionedDimension)[dimension]
                ?: throw Exception("invalid dimension '$dimension'")
            val toPath = (if (!replace) versionedDimension else clientDimensions)[dimension]
                ?: throw Exception("invalid dimension '$dimension'")

            val fromDirectory = File(fromPath)
            if (!fromDirectory.exists()) fromDirectory.mkdirs()

            val fromFiles = fromDirectory.listFiles() ?: throw Exception("cannot find region files of dimension '$dimension'")
            val toDirectory = File(toPath)
            if (!toDirectory.exists()) toDirectory.mkdirs()
            fromFiles.forEach { file -> file.copyTo(File("${toDirectory.absolutePath}/${file.name}"), true) }

            if (reload) WorldLoader.load(plugin, dimension)
            if (returnPlayer) WorldLoader.returnPlayersTo(plugin, dimension)
        }

    }

}