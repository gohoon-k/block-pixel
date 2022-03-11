package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import java.io.File
import java.util.logging.Level

class DenyExecutor(parent: Entry): Executor(parent) {

    override val usage: String = "pixel deny < player >"
    override val description: String = "denies '/pixel' command usage of player"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val target = args[0]
        parent.managers = parent.managers.toMutableSet().apply {
            remove("")
            remove(target)
        }.toSet()
        File("${parent.dataFolder}/pixel.managers").writeBytes(parent.managers.joinToString("\n").toByteArray())
        parent.logger.log(Level.INFO, "denied $target from pixel manager")
        return CommandExecuteResult(true, "current pixel managers are ${parent.managers.joinToString(", ")}")
    }

    override fun autoComplete(args: List<String>): MutableList<String> = mutableListOf()

}