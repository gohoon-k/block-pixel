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
            "Created by dummy kiwi dog!",
            "Palette is really amazing structure!",
            "Want some breaks? Now is the best timing!"
        )

        private val environments = mutableMapOf<String, World.Environment>()

        private lateinit var playersFrom: MutableMap<UUID, PlayersFrom>

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
                    playersFrom[it.uniqueId] = PlayersFrom.fromLocation(it.world.name, it.location)
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
                plugin.server.onlinePlayers.filter { it.world.uid == void?.uid && playersFrom[it.uniqueId]?.world == world.name }.forEach {
                    val from = playersFrom[it.uniqueId] ?: return@forEach
                    it.teleport(Location(world, from.x, from.y, from.z, from.yaw, from.pitch))
                    it.setGravity(true)
                    playersFrom.remove(it.uniqueId)
                }
            })
        }

        fun returnLocation(original: Location, uniqueId: UUID, plugin: JavaPlugin): Location {
            val from = playersFrom[uniqueId] ?: return original
            val world = plugin.server.getWorld(from.world) ?: return original

            playersFrom.remove(uniqueId)
            return Location(world, from.x, from.y, from.z, from.yaw, from.pitch)
        }

        fun enable() {
            val datafile = File("${Entry.repositoryFolder.absolutePath}/../pixel.players_from")
            if (!datafile.exists()) {
                datafile.parentFile.mkdirs()
                datafile.createNewFile()
            }

            val compressedBytes = datafile.readBytes()
            playersFrom = if (compressedBytes.isNotEmpty()) {
                val decompressed = CompressUtils.GZip.decompress(compressedBytes)
                String(decompressed)
                    .split("\n")
                    .map { it.split(" from ") }
                    .associate { UUID.fromString(it[0]) to PlayersFrom.fromString(it[1]) }
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

    data class PlayersFrom(
        val world: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    ) {
        companion object {
            fun fromLocation(world: String, location: Location): PlayersFrom {
                return PlayersFrom(world, location.x, location.y, location.z, location.yaw, location.pitch)
            }
            fun fromString(input: String): PlayersFrom {
                val segments = input.split(", ")
                return PlayersFrom(
                    segments[0],
                    segments[1].toDouble(),
                    segments[2].toDouble(),
                    segments[3].toDouble(),
                    segments[4].toFloat(),
                    segments[5].toFloat()
                )
            }
        }

        override fun toString(): String {
            return "$world, $x, $y, $z, $yaw, $pitch"
        }

    }

}