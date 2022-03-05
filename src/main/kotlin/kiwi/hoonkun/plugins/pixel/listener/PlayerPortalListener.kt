package kiwi.hoonkun.plugins.pixel.listener

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent

class PlayerPortalListener(private val plugin: Entry): Listener {

    private val excludedCauses = listOf(
        PlayerTeleportEvent.TeleportCause.PLUGIN,
        PlayerTeleportEvent.TeleportCause.COMMAND
    )

    @EventHandler
    fun onPlayerPortal(event: PlayerPortalEvent) {

        val cause = event.cause
        val to = event.to ?: return

        if (to.world?.name != Entry.levelName) return

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            event.canCreatePortal = true
        }

        if (!excludedCauses.contains(cause)) {
            event.setTo(Location(plugin.overworld, to.x, to.y, to.z))
        }

    }

}