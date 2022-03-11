package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent

class CommitExecutor(parent: Entry): Executor(parent) {

    companion object {

        val RESULT_NO_COMMIT_MESSAGE =
            CommandExecuteResult(false, "missing arguments. commit message must be specified.")

        const val MESSAGE_NO_REPOSITORY = "repository with given world is not initialized!"

    }

    override val usage: String = "commit < world > < commit_message >"
    override val description: String = "creates new commit to given world's repository, in current branch."

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size == 1)
            return RESULT_NO_COMMIT_MESSAGE

        if (!isValidWorld(args[0]) && args[0] != "all")
            return createUnknownWorldResult(args[0])

        val targets = worldsWithAll(args[0]).toMutableList()
        val excludedTargets = mutableListOf<String>()

        targets.forEach {
            val repo = parent.repositories[it] ?: run {
                if (targets.size == 1)
                    return CommandExecuteResult(
                        false,
                        "$r'$it' $MESSAGE_NO_REPOSITORY\nplease run '/pixel init' first."
                    )

                sender?.sendMessage("$r'$it' $MESSAGE_NO_REPOSITORY\nskipping...")
                excludedTargets.add(it)
                return@forEach
            }

            val head = repo.refDatabase.findRef("HEAD")
            if (head.target.name == "HEAD") {
                if (targets.size == 1)
                    return CommandExecuteResult(
                        false,
                        "$r'$it' $MESSAGE_DETACHED_HEAD\nplease create branch using '/pixel branch' before commit."
                    )

                sender?.sendMessage("$r'$it' $MESSAGE_DETACHED_HEAD\nskipping...")
                excludedTargets.add(it)
            }
        }
        targets.removeAll(excludedTargets)
        if (targets.size == 0) {
            return CommandExecuteResult(
                false,
                "all requested commits are skipped!"
            )
        }

        IOWorker.addToVersionControl(parent, targets)

        return try {
            val hashes = mutableListOf<String>()

            targets.forEach {
                val repo = parent.repositories[it] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

                val git = Git(repo)

                git.add()
                    .addFilepattern(".")
                    .call()

                val commit = git.commit()
                    .setMessage(args.slice(1 until args.size).joinToString(" "))
                    .setCommitter(PersonIdent(repo))
                    .call()

                hashes.add(commit.name.substring(0 until 7))
            }

            CommandExecuteResult(true, "${g}successfully committed world [$w${targets.joinToString("$g, $w")}$g] with hash [$w${hashes.joinToString("$g, $w")}$g]")
        } catch (exception: GitAPIException) {
            return createGitApiFailedResult("commit", exception)
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys.apply { add("all") }
            else -> ARGS_LIST_EMPTY
        }
    }

}