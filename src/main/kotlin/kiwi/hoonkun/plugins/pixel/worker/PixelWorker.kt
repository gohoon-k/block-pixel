package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Entry
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

        suspend fun addToVersionControl(plugin: Entry, dimensions: List<String>) {
            dimensions.forEach { dimension ->
                copyRegions(plugin, dimension, false)
            }
        }

        suspend fun replaceFromVersionControl(plugin: Entry, dimensions: List<String>) {
            dimensions.forEach { dimension ->
                copyRegions(plugin, dimension, true)
            }
        }

        private suspend fun copyRegions(plugin: Entry, dimension: String, replace: Boolean) {
            val worldName = "${Entry.levelName}_$dimension"
            val world = plugin.server.getWorld(worldName) ?: return
            WorldLoader.unload(plugin, world)

            val fromPath = (if (!replace) clientDimensions else versionedDimension)[dimension]
                ?: throw Exception("invalid dimension '$dimension'")
            val toPath = (if (!replace) versionedDimension else clientDimensions)[dimension]
                ?: throw Exception("invalid dimension '$dimension'")

            val fromFiles = File(fromPath).listFiles() ?: throw Exception("cannot find region files of dimension '$dimension'")
            val toDirectory = File(toPath)
            if (!toDirectory.exists()) toDirectory.mkdirs()
            fromFiles.forEach { file -> file.copyTo(File("${toDirectory.absolutePath}/${file.name}"), true) }

            WorldLoader.load(plugin, world)
        }

    }

}