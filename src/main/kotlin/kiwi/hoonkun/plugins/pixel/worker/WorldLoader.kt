package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kiwi.hoonkun.plugins.pixel.utils.CompressUtils
import kotlinx.coroutines.delay
import org.bukkit.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*

class WorldLoader {

    companion object {

        private val loadingMessages = listOf(
            "I tried to print progress of world loading, but failed...",
            "How about taking some breaks while loading?",
            "Created by dummy kiwi dodge!",
            "Palette is really amazing structure!",
            "Want some breaks? Now is the best timing!"
        )

        private val environments = mutableMapOf<String, World.Environment>()

        private lateinit var playersFrom: MutableMap<UUID, String>

        private fun getWorld(plugin: JavaPlugin, worldName: String): World = plugin.server.getWorld(worldName)!!

        suspend fun unload(plugin: JavaPlugin, worldName: String) {
            unload(plugin, getWorld(plugin, worldName))
        }

        private suspend fun unload(plugin: JavaPlugin, world: World) {
            var unloaded = false

            environments[world.name] = world.environment

            plugin.server.scheduler.runTask(plugin, Runnable {
                do {
                    unloaded = plugin.server.unloadWorld(world, true)
                } while (!unloaded)
            })

            while (!unloaded) { delay(100) }
        }

        suspend fun load(plugin: JavaPlugin, worldName: String) {
            var worldLoaded = false
            var waitTime = 0L

            val creator = WorldCreator(worldName).environment(environments.getValue(worldName))

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.createWorld(creator)!!.also { created ->
                    worldLoaded = true
                    created.loadedChunks.forEach { chunk ->
                        chunk.unload()
                        chunk.load()
                    }
                }
            })

            var messageIndex = 0
            val message = loadingMessages.shuffled()
            while (!worldLoaded) {
                delay(200)
                if (worldLoaded) continue

                waitTime += 100
                if (waitTime % 5000L == 0L && messageIndex < message.size * 2) {
                    messageIndex++
                }
                if (messageIndex % 2 == 0) Executor.sendTitle("loading '$worldName' world, this may take some time.")
                else Executor.sendTitle(message[messageIndex / 2])
            }
            Executor.sendTitle(" ")
        }

        fun movePlayersTo(plugin: JavaPlugin, worldName: String) {
            movePlayersTo(plugin, getWorld(plugin, worldName))
        }

        private fun movePlayersTo(plugin: JavaPlugin, world: World) {
            val void = plugin.server.getWorld(Entry.VOID_WORLD_NAME)

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.onlinePlayers.filter { it.world.uid == world.uid }.forEach {
                    playersFrom[it.uniqueId] = it.world.name
                    it.setGravity(false)
                    it.teleport(Location(void, it.location.x, it.location.y, it.location.z, it.location.yaw, it.location.pitch))
                }
            })
        }

        fun returnPlayersTo(plugin: JavaPlugin, worldName: String) {
            returnPlayersTo(plugin, getWorld(plugin, worldName))
        }

        private fun returnPlayersTo(plugin: JavaPlugin, world: World) {
            val void = plugin.server.getWorld(Entry.VOID_WORLD_NAME)

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.onlinePlayers.filter { it.world.uid == void?.uid && playersFrom[it.uniqueId] == world.name }.forEach {
                    it.teleport(Location(world, it.location.x, it.location.y, it.location.z, it.location.yaw, it.location.pitch))
                    it.setGravity(true)
                    playersFrom.remove(it.uniqueId)
                }
            })
        }

        fun returnPlayer(original: Location, uniqueId: UUID, plugin: JavaPlugin): Location {
            val world = plugin.server.getWorld(playersFrom[uniqueId] ?: return original) ?: return original

            playersFrom.remove(uniqueId)
            return Location(world, original.x, original.y, original.z, original.yaw, original.pitch)
        }

        fun enable() {
            val datafile = File("${Entry.repositoryFolder.absolutePath}/../pixel.players_from")
            if (!datafile.exists()) datafile.createNewFile()

            val compressedBytes = datafile.readBytes()
            playersFrom = if (compressedBytes.isNotEmpty()) {
                val decompressed = CompressUtils.GZip.decompress(compressedBytes)
                String(decompressed)
                    .split("\n")
                    .map { it.split(" from ") }
                    .associate { UUID.fromString(it[0]) to it[1] }
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
        }

        fun disable() {
            val datafile = File("${Entry.repositoryFolder.absolutePath}/../pixel.players_from")
            val content = playersFrom.entries.joinToString("\n ") { "${it.key} from ${it.value}" }

            if (content.isEmpty()) {
                datafile.delete()
            } else {
                val compressed = CompressUtils.GZip.compress(content.toByteArray())
                datafile.writeBytes(compressed)
            }
        }

    }

}