package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException

class ResetExecutor(parent: Entry): Executor(parent) {

    companion object {

        val RESULT_SUCCESS =
            CommandExecuteResult(true, "${g}successfully reset commits.")

    }

    override val usage: String = "reset < world > < commit | steps >"
    override val description: String = "reset given world to specified commit or N steps backward commit"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 2)
            return createNotEnoughArgumentsResult(listOf(2), args.size)

        val world = args[0]
        val commit = args[1]
        val steps = args[1].toIntOrNull()

        if (!isValidWorld(world))
            return createUnknownWorldResult(world)

        val repo = parent.repositories[world] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        try {
            Git(repo).reset()
                .setMode(ResetCommand.ResetType.HARD)
                .setRef(if (steps != null && steps <= 10) "HEAD~$steps" else commit)
                .call()

            IOWorker.replaceFromVersionControl(parent, world)
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