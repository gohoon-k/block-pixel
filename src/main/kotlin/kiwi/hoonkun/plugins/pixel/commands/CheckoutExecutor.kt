package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.CheckoutResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Repository

class CheckoutExecutor(parent: Entry): Executor(parent) {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("-recover")

    }

    override val usage: String = "checkout < world > < branch | commit > < committed >"
    override val description: String = "checkout to given branch or commit with specified world's repository"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 2)
            return createNotEnoughArgumentsResult(listOf(2, 3), args.size)

        val world = args[0]
        val name = args[1]

        if (!isValidWorld(world))
            return createUnknownWorldResult(world)

        val repo = parent.repositories[world] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        if (name == "-recover")
            return recover(repo, world)

        if (args.size < 3)
            return createNotEnoughArgumentsResult(listOf(3), args.size)

        val commitConfirm = args[2]

        if (commitConfirm != "true")
            return RESULT_UNCOMMITTED

        try {
            val command = Git(repo).checkout().setName(name)
            command.call()

            if (command.result.status != CheckoutResult.Status.OK) {
                return CommandExecuteResult(false, "failed to checkout, status is '${command.result.status.name}'")
            }

            parent.updateBranch()

            IOWorker.replaceFromVersionControl(parent, world)
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("checkout", exception)
        } catch (exception: UnknownWorldException) {
            return createUnknownWorldResult(exception)
        }

        return CommandExecuteResult(true, "${g}successfully checkout to '$w$name$g'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys
            2 -> {
                SECOND_ARGS_LIST.toMutableList()
                    .apply {
                        addAll(parent.branches[args[0]]
                            ?.toMutableList()
                            ?.apply { remove(parent.branch[args[0]]) }
                                ?: mutableListOf())
                    }
            }
            3 -> if (args[1] != "-recover") ARGS_LIST_COMMIT_CONFIRM else ARGS_LIST_EMPTY
            else -> ARGS_LIST_EMPTY
        }
    }

    private suspend fun recover(repo: Repository, world: String): CommandExecuteResult {
        Git(repo).checkout().setStartPoint("HEAD").setAllPaths(true).call()
        IOWorker.replaceFromVersionControl(parent, world)
        return CommandExecuteResult(true, "cleaned versioned directory to ${repo.branch}")
    }

}