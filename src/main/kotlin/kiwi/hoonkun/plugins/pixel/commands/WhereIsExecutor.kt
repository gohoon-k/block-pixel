package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class WhereIsExecutor(parent: Entry): Executor(parent) {

    override val usage: String = "whereis < player >"
    override val description: String = "prints world of given player presents"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val target = if (args[0] == "me" && sender is Player) {
            sender
        } else {
            parent.server.getPlayer(args[0])!!
        }

        val where = if (target.world.name == Entry.levelName) "dummy" else target.world.name.replace("${Entry.levelName}_", "")
        return CommandExecuteResult(true, "${g}player '$w${args[0]}$g' is in '$w$where$g'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.server.onlinePlayers.map { it.name }.toMutableList()
            else -> ARGS_LIST_EMPTY
        }
    }

}