package kiwi.hoonkun.plugins.pixel.commands

import org.bukkit.command.CommandSender

abstract class Executor {

    abstract fun exec(sender: CommandSender, args: List<String>): Boolean

    abstract fun autoComplete(args: List<String>): MutableList<String>

}