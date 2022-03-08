package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException

class ResetExecutor(private val plugin: Entry): Executor() {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("< steps >", "< commit_hash >")

    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val repo = Entry.repository ?: return invalidRepositoryResult

        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing. back steps or commit name must be specified.")

        val target = args[1].toIntOrNull()

        try {
            Git(repo).reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(if (target != null && target <= 10) "HEAD~${args[1]}" else args[1])
                .call()

            IOWorker.replaceFromVersionControl(plugin, dimensions(args[0]))
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("reset", exception)
        } catch (exception: UnknownDimensionException) {
            return createDimensionExceptionResult(exception)
        }

        return CommandExecuteResult(true, "${g}successfully reset commits.")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> ARGS_LIST_DIMENSIONS
            2 -> SECOND_ARGS_LIST
            else -> ARGS_LIST_EMPTY
        }
    }

}