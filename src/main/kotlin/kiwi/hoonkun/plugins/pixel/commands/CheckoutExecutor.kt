package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.PixelWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.CheckoutResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class CheckoutExecutor(private val plugin: Entry): Executor() {

    companion object {

        val COMPLETE_LIST_0 = mutableListOf("<branch_name>", "<commit_hash>")

    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing, target dimension must be specified.")

        if (args.size == 1)
            return CommandExecuteResult(false, "argument is missing, checkout target must be specified.")

        if (args.size == 2)
            return CommandExecuteResult(false, "you must specify that you have committed all uncommitted changes before checkout.\nif yes, pass 'true' to last argument.")

        if (args[2] != "true")
            return uncommittedChangesResult

        val repo = Entry.repository ?: return invalidRepositoryResult

        try {
            val command = Git(repo).checkout().setName(args[1])
            command.call()

            if (command.result.status != CheckoutResult.Status.OK) {
                return CommandExecuteResult(false, "failed to checkout, status is '${command.result.status.name}'")
            }

            PixelWorker.replaceFromVersionControl(plugin, dimensions(args[0]))
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult(exception)
        }

        return CommandExecuteResult(true, "successfully checkout to '${args[1]}'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_DIMENSIONS
            2 -> COMPLETE_LIST_0
            else -> COMPLETE_LIST_EMPTY
        }
    }

}