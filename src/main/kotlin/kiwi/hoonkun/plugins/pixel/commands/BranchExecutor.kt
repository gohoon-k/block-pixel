package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender

class BranchExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val create = spawn(listOf("git", "branch", args[0]), Entry.versionedFolder!!)
            .handle(
                "successfully created new branch: ${args[0]}. checkout...",
                "failed to create branch: ${args[0]}. aborting..."
            )

        if (!create.success) return create

        return CheckoutExecutor().exec(sender, listOf(args[0]))
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}