package kiwi.hoonkun.plugins.pixel.commands

import org.bukkit.command.CommandSender

class MergeExecutor: Executor() {

    override fun exec(sender: CommandSender, args: List<String>): Boolean {
        return true
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}