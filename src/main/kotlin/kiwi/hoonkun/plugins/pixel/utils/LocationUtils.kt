package kiwi.hoonkun.plugins.pixel.utils

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.Location
import org.bukkit.entity.Player

class LocationUtils {

    companion object {

        fun applyLocation(
            plugin: Entry,
            player: Player,
            original: Location,
            message: String,
            applier: (Location) -> Unit
        ) {
            val overworld = plugin.server.getWorld("${Entry.levelName}_overworld")

            if (!original.isWorldLoaded || (original.world?.name == Entry.levelName && overworld == null)) {
                player.sendMessage(message)
                applier.invoke(Location(plugin.void, original.x, original.y, original.z))
                player.setGravity(false)
            } else {
                if (original.world?.name == Entry.levelName && overworld != null)
                    applier.invoke(Location(overworld, original.x, original.y, original.z))
                else
                    applier.invoke(original)
            }
        }

    }

}