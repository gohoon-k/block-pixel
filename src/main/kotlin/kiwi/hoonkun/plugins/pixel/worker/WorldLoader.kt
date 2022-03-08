package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kotlinx.coroutines.delay
import org.bukkit.*

class WorldLoader {

    companion object {

        private val loadingMessages = listOf(
            "I tried to print progress of world loading, but failed...",
            "How about taking some breaks while loading?",
            "Created by dummy kiwi dodge!",
            "Palette is really amazing structure!",
            "Want some breaks? Now is the best timing!"
        )

        val lightSourceBlocks = listOf(
            "minecraft:beacon",
            "minecraft:campfire",
            "minecraft:cauldron",
            "minecraft:conduit",
            "minecraft:end_gateway",
            "minecraft:end_portal",
            "minecraft:fire",
            "minecraft:glowstone",
            "minecraft:jack_o_lantern",
            "minecraft:lava",
            "minecraft:lantern",
            "minecraft:redstone_lamp",
            "minecraft:respawn_anchor",
            "minecraft:sea_lantern",
            "minecraft:sea_pickle",
            "minecraft:shroomlight",
            "minecraft:cave_vines",
            "minecraft:cave_vines_plant",
            "minecraft:end_rod",
            "minecraft:torch",
            "minecraft:blast_furnace",
            "minecraft:furnace",
            "minecraft:smoker",
            "minecraft:candles",
            "minecraft:nether_portal",
            "minecraft:crying_obsidian",
            "minecraft:soul_campfire",
            "minecraft:soul_fire",
            "minecraft:soul_lantern",
            "minecraft:soul_torch",
            "minecraft:deepslate_redstone_ore",
            "minecraft:redstone_ore",
            "minecraft:enchanting_table",
            "minecraft:ender_chest",
            "minecraft:glow_lichen",
            "minecraft:redstone_torch",
            "minecraft:amethyst_cluster",
            "minecraft:small_amethyst_bud",
            "minecraft:medium_amethyst_bud",
            "minecraft:large_amethyst_bud",
            "minecraft:magma_block",
            "minecraft:brewing_stand",
            "minecraft:brown_mushroom",
            "minecraft:dragon_egg",
            "minecraft:end_portal_frame",
            "minecraft:sculk_sensor",
            "minecraft:light_block",
        )

        private val lightSources = mutableListOf<Triple<Int, Int, Int>>()

        private fun getWorld(plugin: Entry, dimension: String): World = plugin.server.getWorld("${Entry.levelName}_$dimension")!!

        suspend fun unload(plugin: Entry, dimension: String) {
            unload(plugin, getWorld(plugin, dimension))
        }

        private suspend fun unload(plugin: Entry, world: World) {
            var unloaded = false

            plugin.server.scheduler.runTask(plugin, Runnable {
                world.isAutoSave = false
                world.save()

                do {
                    unloaded = plugin.server.unloadWorld(world, true)
                } while (!unloaded)
            })

            while (!unloaded) { delay(100) }
        }

        suspend fun load(plugin: Entry, dimension: String) {
            val worldName = "${Entry.levelName}_$dimension"

            var worldLoaded = false
            var waitTime = 0L

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.createWorld(WorldCreator(worldName))!!.also { created ->
                    worldLoaded = true

                    created.isAutoSave = true
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
                if (messageIndex % 2 == 0) Executor.sendTitle("loading '$dimension' world, this may take some time.")
                else Executor.sendTitle(message[messageIndex / 2])
            }
            Executor.sendTitle(" ")
        }

        suspend fun updateLights(plugin: Entry, dimension: String) {
            updateLights(plugin, getWorld(plugin, dimension))
        }

        private suspend fun updateLights(plugin: Entry, world: World) {
            var complete = false

            plugin.server.scheduler.runTask(plugin, Runnable {
                lightSources.forEachIndexed { index, (x, y, z) ->
                    Executor.sendTitle("updating light sources [$index/${lightSources.size}]")
                    val block = world.getBlockAt(x, y, z)
                    val blockData = block.blockData
                    val blockState = block.state
                    world.setBlockData(x, y, z, Material.AIR.createBlockData())
                    world.setBlockData(x, y, z, blockData)
                    blockState.update(true, true)
                }
                lightSources.clear()

                complete = true
            })

            while (!complete) { delay(100) }
        }

        fun movePlayersTo(plugin: Entry, dimension: String) {
            movePlayersTo(plugin, getWorld(plugin, dimension))
        }

        private fun movePlayersTo(plugin: Entry, world: World) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.onlinePlayers.filter { it.world.uid == world.uid }.forEach {
                    it.setGravity(false)
                    it.teleport(Location(plugin.void, it.location.x, it.location.y + 0.5, it.location.z))
                }
            })
        }

        fun returnPlayersTo(plugin: Entry, dimension: String) {
            returnPlayersTo(plugin, getWorld(plugin, dimension))
        }

        private fun returnPlayersTo(plugin: Entry, world: World) {
            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.onlinePlayers.filter { it.world.uid == plugin.void.uid }.forEach {
                    it.teleport(Location(world, it.location.x, it.location.y, it.location.z))
                    it.setGravity(true)
                }
            })
        }

        fun registerLightSourceLocation(position: Triple<Int, Int, Int>) {
            lightSources.add(position)
        }

    }

}