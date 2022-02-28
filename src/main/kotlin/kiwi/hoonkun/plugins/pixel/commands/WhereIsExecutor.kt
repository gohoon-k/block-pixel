package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class WhereIsExecutor(private val plugin: Entry): Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "missing argument. target must be specified.")

        val target = if (args[0] == "me" && sender is Player) {
            sender
        } else {
            plugin.server.getPlayer(args[0])!!
        }

        val where = if (target.world.name == Entry.levelName) "dummy" else target.world.name.replace("${Entry.levelName}_", "")
        return CommandExecuteResult(true, "player '${args[0]}' is in '$where'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> plugin.server.onlinePlayers.map { it.name }.toMutableList()
            else -> COMPLETE_LIST_EMPTY
        }
    }

}