package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender

class TeleportExecutor(parent: Entry): Executor(parent) {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("dummy", "overworld")

        val RESULT_INVALID_DESTINATION =
            CommandExecuteResult(false, "invalid argument. destination must be one of 'dummy' or 'overworld'")

    }

    override val usage: String = "tp < player > < \"dummy\" | \"overworld\" >"
    override val description: String = "teleport player to specified world"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 2)
            return createNotEnoughArgumentsResult(listOf(2), args.size)

        val player = args[0]
        val destination = args[1]

        if (destination != "dummy" && destination != "overworld")
            return RESULT_INVALID_DESTINATION

        val target = parent.server.getPlayer(player) ?: return CommandExecuteResult(false, "unknown player '${player}'")

        parent.server.scheduler.runTask(parent, Runnable {
            when (destination) {
                "dummy" -> {
                    target.teleport(target.location.clone().apply { world = parent.server.getWorld(Entry.levelName) })
                }
                "overworld" -> {
                    parent.server.getWorld("${Entry.levelName}_overworld").also {
                        if (it == null) sender?.sendMessage("${r}overworld is unloaded by pixel command now.\nplease wait until pixel command finishes.")
                        else target.teleport(target.location.clone().apply { world = it })
                    }
                }
            }
            target.setGravity(true)
        })

        return CommandExecuteResult(true, "${g}successfully teleported '$w${args[0]}$g' to '$w$destination$g'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.server.onlinePlayers.map { it.name }.toMutableList()
            2 -> SECOND_ARGS_LIST
            else -> ARGS_LIST_EMPTY
        }
    }

}