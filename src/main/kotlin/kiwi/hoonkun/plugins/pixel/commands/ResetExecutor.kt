package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender

class ResetExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        return if (args[0].toIntOrNull() != null)
            spawn(listOf("git", "reset", "--hard", "HEAD~${args[0]}"), Entry.versionedFolder!!)
                .handle(
                    "successfully reset HEAD to ${args[0]} behind commit",
                    "failed to reset"
                )
        else
            spawn(listOf("git", "reset", "--hard", args[0]), Entry.versionedFolder!!)
                .handle(
                    "successfully reset HEAD to commit '${args[0]}'",
                    "failed to reset"
                )
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}