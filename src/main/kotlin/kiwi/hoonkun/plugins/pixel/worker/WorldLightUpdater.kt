package kiwi.hoonkun.plugins.pixel.worker

import kiwi.hoonkun.plugins.pixel.Palette
import kiwi.hoonkun.plugins.pixel.commands.Executor
import kotlinx.coroutines.delay
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin

class WorldLightUpdater {

    companion object {

        private val always: (Palette) -> Boolean = { true }
        private val whenLit: (Palette) -> Boolean = { it.properties?.get("lit") == "true" }
        private val whenWaterlogged: (Palette) -> Boolean = { it.properties?.get("waterlogged") == "true" }
        private val whenHasBerries: (Palette) -> Boolean = { it.properties?.get("berries") == "true" }
        private val whenLevelGreaterThanZero: (Palette) -> Boolean = { it.properties?.get("level") != "0" }
        private val whenChargesGreaterThanZero: (Palette) -> Boolean = { it.properties?.get("charges") != "0" }

        private val lightSourceMaterials = mapOf(
            Material.BEACON to always,
            Material.CAMPFIRE to whenLit,
            Material.LAVA_CAULDRON to always,
            Material.CONDUIT to always,
            Material.END_GATEWAY to always,
            Material.END_PORTAL to always,
            Material.FIRE to always,
            Material.GLOWSTONE to always,
            Material.JACK_O_LANTERN to always,
            Material.LAVA to always,
            Material.LANTERN to always,
            Material.REDSTONE_LAMP to whenLit,
            Material.RESPAWN_ANCHOR to whenChargesGreaterThanZero,
            Material.SEA_LANTERN to always,
            Material.SEA_PICKLE to whenWaterlogged,
            Material.SHROOMLIGHT to always,
            Material.CAVE_VINES to whenHasBerries,
            Material.CAVE_VINES_PLANT to whenHasBerries,
            Material.END_ROD to always,
            Material.TORCH to always,
            Material.WALL_TORCH to always,
            Material.BLAST_FURNACE to whenLit,
            Material.FURNACE to whenLit,
            Material.SMOKER to whenLit,
            Material.CANDLE to always,
            Material.BLACK_CANDLE to always,
            Material.BLUE_CANDLE to always,
            Material.BROWN_CANDLE to always,
            Material.CYAN_CANDLE to always,
            Material.GRAY_CANDLE to always,
            Material.GREEN_CANDLE to always,
            Material.LIGHT_BLUE_CANDLE to always,
            Material.LIGHT_GRAY_CANDLE to always,
            Material.LIME_CANDLE to always,
            Material.MAGENTA_CANDLE to always,
            Material.ORANGE_CANDLE to always,
            Material.PINK_CANDLE to always,
            Material.PURPLE_CANDLE to always,
            Material.RED_CANDLE to always,
            Material.WHITE_CANDLE to always,
            Material.YELLOW_CANDLE to always,
            Material.CANDLE_CAKE to always,
            Material.BLACK_CANDLE_CAKE to always,
            Material.BLUE_CANDLE_CAKE to always,
            Material.BROWN_CANDLE_CAKE to always,
            Material.CYAN_CANDLE_CAKE to always,
            Material.GRAY_CANDLE_CAKE to always,
            Material.GREEN_CANDLE_CAKE to always,
            Material.LIGHT_BLUE_CANDLE_CAKE to always,
            Material.LIGHT_GRAY_CANDLE_CAKE to always,
            Material.LIME_CANDLE_CAKE to always,
            Material.MAGENTA_CANDLE_CAKE to always,
            Material.ORANGE_CANDLE_CAKE to always,
            Material.PINK_CANDLE_CAKE to always,
            Material.PURPLE_CANDLE_CAKE to always,
            Material.RED_CANDLE_CAKE to always,
            Material.WHITE_CANDLE_CAKE to always,
            Material.YELLOW_CANDLE_CAKE to always,
            Material.NETHER_PORTAL to always,
            Material.CRYING_OBSIDIAN to always,
            Material.SOUL_CAMPFIRE to whenLit,
            Material.SOUL_FIRE to always,
            Material.SOUL_LANTERN to always,
            Material.SOUL_TORCH to always,
            Material.SOUL_WALL_TORCH to always,
            Material.DEEPSLATE_REDSTONE_ORE to whenLit,
            Material.REDSTONE_ORE to whenLit,
            Material.ENCHANTING_TABLE to always,
            Material.ENDER_CHEST to always,
            Material.GLOW_LICHEN to always,
            Material.REDSTONE_TORCH to whenLit,
            Material.REDSTONE_WALL_TORCH to whenLit,
            Material.AMETHYST_CLUSTER to always,
            Material.SMALL_AMETHYST_BUD to always,
            Material.MEDIUM_AMETHYST_BUD to always,
            Material.LARGE_AMETHYST_BUD to always,
            Material.MAGMA_BLOCK to always,
            Material.BREWING_STAND to always,
            Material.BROWN_MUSHROOM to always,
            Material.DRAGON_EGG to always,
            Material.END_PORTAL_FRAME to always,
            Material.SCULK_SENSOR to always,
            Material.LIGHT to whenLevelGreaterThanZero,
        )

        private val lightSources = lightSourceMaterials.map { "${it.key.key}" to it.value }.toMap()

        private val updateTargets = mutableListOf<Triple<Int, Int, Int>>()

        fun Palette.isEmittingLights(): Boolean {
            if (!lightSources.keys.contains(name)) return false

            return lightSources[name]?.invoke(this) == true
        }

        suspend fun updateLights(plugin: JavaPlugin, worldName: String) {
            val world = plugin.server.getWorld(worldName) ?: throw Exception("unknown world '$worldName'")
            var complete = false

            plugin.server.scheduler.runTask(plugin, Runnable {
                updateTargets.forEachIndexed { index, (x, y, z) ->
                    Executor.sendTitle("updating light sources [$index/${updateTargets.size}]")
                    val block = world.getBlockAt(x, y, z)
                    val blockData = block.blockData
                    val blockState = block.state
                    world.setBlockData(x, y, z, Material.AIR.createBlockData())
                    world.setBlockData(x, y, z, blockData)
                    blockState.update(true, true)
                }
                updateTargets.clear()

                complete = true
            })

            while (!complete) { delay(100) }
        }

        fun addTarget(value: Triple<Int, Int, Int>) {
            updateTargets.add(value)
        }

    }

}