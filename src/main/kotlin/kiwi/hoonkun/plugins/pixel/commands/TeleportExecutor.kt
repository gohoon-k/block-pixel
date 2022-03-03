package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.Location
import org.bukkit.command.CommandSender

class TeleportExecutor(private val plugin: Entry): Executor() {

    companion object {
        val COMPLETE_LIST_1 = mutableListOf("dummy", "overworld")
    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "missing arguments. destination and target must be specified.")

        val target = plugin.server.getPlayer(args[0]) ?: return CommandExecuteResult(false, "unknown player '${args[0]}'")

        if (args.size == 1)
            return CommandExecuteResult(false, "missing argument. destination must be specified.")

        val destination = args[1]

        if (destination != "dummy" && destination != "overworld")
            return CommandExecuteResult(false, "invalid argument. destination must be one of 'dummy' or 'overworld'")

        plugin.server.scheduler.runTask(plugin, Runnable {
            when (destination) {
                "dummy" -> target.teleport(Location(plugin.server.getWorld(Entry.levelName), target.location.x, target.location.y, target.location.z))
                "overworld" -> target.teleport(Location(plugin.overworld, target.location.x, target.location.y, target.location.z))
            }
        })

        return CommandExecuteResult(true, "${g}successfully teleported '$w${args[0]}$g' to '$w$destination$g'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> plugin.server.onlinePlayers.map { it.name }.toMutableList()
            2 -> COMPLETE_LIST_1
            else -> COMPLETE_LIST_EMPTY
        }
    }

}