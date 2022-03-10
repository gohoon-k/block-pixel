package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.CheckoutResult
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException

class CheckoutExecutor(parent: Entry): Executor(parent) {

    companion object {

        val SECOND_ARGS_LIST = mutableListOf("< branch_name | commit_hash >", "-recover")

        val RESULT_NO_CHECKOUT_TARGET =
            CommandExecuteResult(false, "argument is missing, checkout target must be specified.")

    }

    override val usage: String = "checkout checkout < branch_name | commit_hash > < commit_confirm >"
    override val description: String = "checkout to given branch or commit with specified world's repository"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size == 1)
            return RESULT_NO_CHECKOUT_TARGET

        if (!isValidWorld(args[0]))
            return createUnknownWorldResult(args[0])

        val repo = parent.repositories[args[0]] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        val git = Git(repo)
        val targets = worlds(args[0])

        if (args.size == 2 && args[1] == "-recover") {
            git.checkout().setStartPoint("HEAD").setAllPaths(true).call()
            IOWorker.replaceFromVersionControl(parent, targets)
            return CommandExecuteResult(true, "cleaned versioned directory to ${repo.branch}")
        }

        if (args.size == 2)
            return RESULT_NO_COMMIT_CONFIRM

        if (args[2] != "true")
            return RESULT_UNCOMMITTED

        try {
            val command = git.checkout().setName(args[1])
            command.call()

            if (command.result.status != CheckoutResult.Status.OK) {
                return CommandExecuteResult(false, "failed to checkout, status is '${command.result.status.name}'")
            }

            IOWorker.replaceFromVersionControl(parent, targets)
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("checkout", exception)
        } catch (exception: UnknownWorldException) {
            return createUnknownWorldResult(exception)
        }

        return CommandExecuteResult(true, "${g}successfully checkout to '$w${args[1]}$g'")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys
            2 -> SECOND_ARGS_LIST
            3 -> if (args[1] != "-recover") ARGS_LIST_COMMIT_CONFIRM else ARGS_LIST_EMPTY
            else -> ARGS_LIST_EMPTY
        }
    }

}