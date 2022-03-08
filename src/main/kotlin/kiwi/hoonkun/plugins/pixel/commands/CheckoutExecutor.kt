package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.CheckoutResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class CheckoutExecutor(private val plugin: Entry): Executor() {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("< branch_name | commit_hash >", "-recover")

    }

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val repo = Entry.repository ?: return invalidRepositoryResult

        if (args.isEmpty())
            return CommandExecuteResult(false, "argument is missing, target dimension must be specified.")

        if (args.size == 1)
            return CommandExecuteResult(false, "argument is missing, checkout target must be specified.")

        val git = Git(repo)

        if (args[0] == "-recover") {
            git.checkout().setStartPoint("HEAD").setAllPaths(true).call()
            IOWorker.replaceFromVersionControl(plugin, dimensions(args[0]))
            return CommandExecuteResult(true, "cleaned versioned directory to ${repo.branch}")
        }

        if (args.size == 2)
            return CommandExecuteResult(false, "you must specify that you have committed all uncommitted changes before checkout.\nif yes, pass 'true' to last argument.")

        if (args[2] != "true")
            return uncommittedChangesResult

        try {
            val command = git.checkout().setName(args[1])
            command.call()

            if (command.result.status != CheckoutResult.Status.OK) {
                return CommandExecuteResult(false, "failed to checkout, status is '${command.result.status.name}'")
            }

            IOWorker.replaceFromVersionControl(plugin, dimensions(args[0]))
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("checkout", exception)
        } catch (exception: UnknownDimensionException) {
            return createDimensionExceptionResult(exception)
        }

        return CommandExecuteResult(true, "${g}successfully checkout to '$w${args[1]}$g'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> ARGS_LIST_DIMENSIONS
            2 -> SECOND_ARGS_LIST
            3 -> if (args[1] != "-recover") ARGS_LIST_COMMIT_CONFIRM else ARGS_LIST_EMPTY
            else -> ARGS_LIST_EMPTY
        }
    }

}