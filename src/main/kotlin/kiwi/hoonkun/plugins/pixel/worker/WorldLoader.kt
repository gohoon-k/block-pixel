package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kotlinx.coroutines.delay
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator

class WorldLoader {

    companion object {

        private val loadingMessages = listOf(
            "I tried to print progress of world loading, but failed...",
            "How about taking some breaks while loading?",
            "Created by dummy kiwi dodge!",
            "Palette is really amazing structure!",
            "Want some breaks? Now is the best timing!"
        )

        suspend fun unload(plugin: Entry, world: World) {
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

        suspend fun load(plugin: Entry, world: World) {
            var loaded = false
            var waitTime = 0L

            Executor.sendTitle("reloading world, this may take some time.")

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.createWorld(WorldCreator(world.name))!!.also { created ->
                    created.isAutoSave = true
                    created.loadedChunks.forEach { chunk ->
                        chunk.unload()
                        chunk.load()
                    }
                    plugin.server.onlinePlayers.filter { it.world.uid == plugin.void.uid }.forEach {
                        it.teleport(Location(created, it.location.x, it.location.y, it.location.z))
                        it.setGravity(true)
                    }
                    loaded = true
                }
            })

            val message = loadingMessages.shuffled()
            while (!loaded) {
                delay(100)
                waitTime += 100
                if (waitTime % 5000L == 0L && (waitTime / 5000).toInt() - 1 < message.size) {
                    Executor.sendTitle(message[(waitTime / 5000).toInt() - 1])
                }
            }
            Executor.sendTitle(" ")
        }

    }

}