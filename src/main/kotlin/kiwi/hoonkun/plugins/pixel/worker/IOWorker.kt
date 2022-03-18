package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.*

import java.io.File

class IOWorker {

    companion object {

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

                val fromPath = if (!replace) anvilType.getClient(world) else anvilType.getRepository(world)
                val toPath = if (!replace) anvilType.getRepository(world) else anvilType.getClient(world)

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