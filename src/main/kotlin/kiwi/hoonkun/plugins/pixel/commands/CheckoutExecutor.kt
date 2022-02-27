package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender

class CheckoutExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        return spawn(listOf("git", "checkout", args[0]), Entry.versionedFolder!!)
            .handle(
                "successfully checkout to ${args[0]}.",
                "failed to checkout."
            )
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}