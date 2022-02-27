package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.CheckoutResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class CheckoutExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing, checkout target must be specified.")

        val repo = Entry.repository ?: return invalidRepositoryResult

        try {
            val command = Git(repo).checkout().addPath(args[0])
            command.call()

            if (command.result.status != CheckoutResult.Status.OK) {
                return CommandExecuteResult(false, "failed to checkout, status is '${command.result.status.name}'")
            }

            WriteWorker.versioned2client(listOf("overworld", "nether", "the_end"))
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult(exception)
        }

        return CommandExecuteResult(true, "successfully checkout to '${args[0]}'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}