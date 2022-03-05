package kiwi.hoonkun.plugins.pixel.listener

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.spigotmc.event.player.PlayerSpawnLocationEvent

class PlayerSpawnListener(private val plugin: Entry): Listener {

    @EventHandler
    fun onPlayerSpawn(event: PlayerSpawnLocationEvent) {
        val location = event.spawnLocation

        if (location.world?.name != Entry.levelName) return

        event.spawnLocation = Location(plugin.overworld, location.x, location.y, location.z)
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val location = event.respawnLocation

        if (location.world?.name != Entry.levelName) return

        event.respawnLocation = Location(plugin.overworld, location.x, location.y, location.z)
    }

}