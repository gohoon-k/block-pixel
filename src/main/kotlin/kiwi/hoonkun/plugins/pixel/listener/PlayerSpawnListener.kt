package kiwi.hoonkun.plugins.pixel.listener

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.utils.LocationUtils
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.spigotmc.event.player.PlayerSpawnLocationEvent

class PlayerSpawnListener(private val plugin: Entry): Listener {

    @EventHandler
    fun onPlayerSpawn(event: PlayerSpawnLocationEvent) {
        LocationUtils.applyLocation(
            plugin,
            event.player,
            event.spawnLocation,
            "world which you are trying to join is unloaded by pixel command, so you are joined to void world.\nplease wait until pixel command finishes."
        ) {
            event.spawnLocation = it
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        LocationUtils.applyLocation(
            plugin,
            event.player,
            event.respawnLocation,
            "world which you are trying to respawn is now unloaded by pixel command.\nplease wait until pixel command finishes."
        ) {
            event.respawnLocation = it
        }
    }

}