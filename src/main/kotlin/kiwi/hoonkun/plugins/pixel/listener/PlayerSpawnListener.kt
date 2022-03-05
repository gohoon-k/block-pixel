package kiwi.hoonkun.plugins.pixel.listener

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.spigotmc.event.player.PlayerSpawnLocationEvent

class PlayerSpawnListener(private val plugin: Entry): Listener {

    @EventHandler
    fun onPlayerSpawn(event: PlayerSpawnLocationEvent) {
        if (event.player.hasPlayedBefore()) return

        val location = event.spawnLocation

        event.spawnLocation = Location(plugin.overworld, location.x, location.y, location.z)
    }

}