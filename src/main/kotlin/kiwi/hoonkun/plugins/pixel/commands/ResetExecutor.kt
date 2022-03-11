package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException

class ResetExecutor(parent: Entry): Executor(parent) {

    companion object {

        val RESULT_NO_TARGET =
            CommandExecuteResult(false, "argument is missing. steps or commit hash must be specified.")

        val RESULT_SUCCESS =
            CommandExecuteResult(true, "${g}successfully reset commits.")

    }

    override val usage: String = "reset < world > < commit | steps >"
    override val description: String = "reset given world to specified commit or N steps backward commit"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (!isValidWorld(args[0]))
            return createUnknownWorldResult(args[0])

        val repo = parent.repositories[args[0]] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        if (args.size == 1)
            return RESULT_NO_TARGET

        val target = args[1].toIntOrNull()

        try {
            Git(repo).reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(if (target != null && target <= 10) "HEAD~${args[1]}" else args[1])
                .call()

            IOWorker.replaceFromVersionControl(parent, worlds(args[0]))
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("reset", exception)
        } catch (exception: UnknownWorldException) {
            return createUnknownWorldResult(exception)
        }

        return RESULT_SUCCESS
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys
            else -> ARGS_LIST_EMPTY
        }
    }

}