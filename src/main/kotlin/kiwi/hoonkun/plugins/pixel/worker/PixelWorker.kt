package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.read
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toNBT

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

        fun read(dimensions: List<String>): List<NBT<Chunk>> {
            val result = mutableListOf<NBT<Chunk>>()
            dimensions.forEach { dimension ->
                val dimensionPath = versionedDimension[dimension] ?: throw Exception("invalid dimension")
                val anvilFiles = File(dimensionPath).listFiles() ?: return@forEach
                result.add(anvilFiles.read().toNBT { timestamp, nbt -> Chunk(timestamp, nbt) })
            }
            return result
        }

        suspend fun Anvils.writeToClient(plugin: Entry, dimension: String) {
            val path = clientDimensions[dimension] ?: throw Exception("invalid dimension")
            WorldLoader.unload(plugin, dimension)
            entries.forEach { (location, bytes) ->
                val file = File("$path/r.${location.x}.${location.z}.mca")
                file.writeBytes(bytes)
            }
            WorldLoader.load(plugin, dimension)
        }

        suspend fun addToVersionControl(
            plugin: Entry,
            dimensions: List<String>,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            dimensions.forEach { dimension ->
                copyRegions(plugin, dimension, false, needsUnload, needsLoad)
            }
        }

        suspend fun replaceFromVersionControl(
            plugin: Entry,
            dimensions: List<String>,
            needsUnload: Boolean = true,
            needsLoad: Boolean = true
        ) {
            dimensions.forEach { dimension ->
                copyRegions(plugin, dimension, true, needsUnload, needsLoad)
            }
        }

        private suspend fun copyRegions(
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

            if (needsLoad) {
                WorldLoader.load(plugin, dimension)
                WorldLoader.returnPlayersTo(plugin, dimension)
            }
        }

    }

}