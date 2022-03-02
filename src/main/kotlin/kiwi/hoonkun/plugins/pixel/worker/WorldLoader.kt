package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kotlinx.coroutines.delay
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator

class WorldLoader {

    companion object {

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

            while (!loaded) {
                delay(100)
                waitTime += 100
                if (waitTime == 5000L) {
                    Executor.sendTitle("I tried to print progress of world loading, but failed...")
                }
                if (waitTime == 10000L) {
                    Executor.sendTitle("How about taking some breaks?")
                }
                if (waitTime == 15000L) {
                    Executor.sendTitle("Wow, its really big world.")
                }
            }
        }

    }

}