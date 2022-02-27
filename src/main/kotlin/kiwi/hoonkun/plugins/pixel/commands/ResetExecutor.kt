package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException

class ResetExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing. back steps or commit name must be specified.")

        val repo = Entry.repository ?: return invalidRepositoryResult

        try {
            Git(repo).reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(if (args[0].toIntOrNull() != null) "HEAD~${args[0]}" else args[0])
                .call()
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult(exception)
        }

        WriteWorker.versioned2client(listOf("overworld", "nether", "the_end"))

        return CommandExecuteResult(true, "successfully reset commits.")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}