package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kotlinx.coroutines.delay
import org.bukkit.Location
import org.bukkit.Material
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
            var worldLoaded = false
            var lightUpdated = false
            var waitTime = 0L

            Executor.sendTitle("reloading world, this may take some time.")

            plugin.server.scheduler.runTask(plugin, Runnable {
                plugin.server.createWorld(WorldCreator(world.name))!!.also { created ->
                    worldLoaded = true

                    created.isAutoSave = true
                    created.loadedChunks.forEach { chunk ->
                        chunk.unload()
                        chunk.load()
                    }
                    lightSources.forEachIndexed { index, (x, y, z) ->
                        Executor.sendTitle("updating light sources [$index/${lightSources.size}]")
                        val block = created.getBlockAt(x, y, z)
                        val blockData = block.blockData
                        val blockState = block.state
                        created.setBlockData(x, y, z, Material.AIR.createBlockData())
                        created.setBlockData(x, y, z, blockData)
                        blockState.update(true, true)
                    }
                    lightSources.clear()
                    plugin.server.onlinePlayers.filter { it.world.uid == plugin.void.uid }.forEach {
                        it.teleport(Location(created, it.location.x, it.location.y, it.location.z))
                        it.setGravity(true)
                    }
                    lightUpdated = true
                }
            })

            val message = loadingMessages.shuffled()
            while (!worldLoaded || !lightUpdated) {
                delay(100)
                if (worldLoaded) continue

                waitTime += 100
                if (waitTime % 5000L == 0L && (waitTime / 5000).toInt() - 1 < message.size) {
                    Executor.sendTitle(message[(waitTime / 5000).toInt() - 1])
                }
            }
            Executor.sendTitle(" ")
        }

        fun registerLightSourceLocation(position: Triple<Int, Int, Int>) {
            lightSources.add(position)
        }

    }

}