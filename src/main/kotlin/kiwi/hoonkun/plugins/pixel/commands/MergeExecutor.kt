package kiwi.hoonkun.plugins.pixel.commands

import org.bukkit.command.CommandSender

class MergeExecutor: Executor() {

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        TODO("implement this function")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}