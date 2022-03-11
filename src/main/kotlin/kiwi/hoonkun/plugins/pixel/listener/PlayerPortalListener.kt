package kiwi.hoonkun.plugins.pixel.listener

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.utils.LocationUtils
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent

class PlayerPortalListener(private val plugin: Entry): Listener {

    @EventHandler
    fun onPlayerPortal(event: PlayerPortalEvent) {

        val cause = event.cause
        val to = event.to ?: return

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            event.canCreatePortal = true
        }

        LocationUtils.applyLocation(
            plugin,
            event.player,
            to,
            "world which you are trying to enter is unloaded by pixel command.\nplease wait until command finishes."
        ) {
            if (it.world?.name == Entry.VOID_WORLD_NAME) event.isCancelled = true
            else event.setTo(it)
        }

    }

}