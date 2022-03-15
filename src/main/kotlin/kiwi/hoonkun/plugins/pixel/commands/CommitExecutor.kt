package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent

class CommitExecutor(parent: Entry): Executor(parent) {

    companion object {

        const val MESSAGE_NO_REPOSITORY = "repository with given world is not initialized!"

        const val SUGGEST_NO_REPOSITORY = "please run '/pixel init' first."
        const val SUGGEST_DETACHED_HEAD = "please create branch using '/pixel branch' before commit."

    }

    override val usage: String = "commit < world > < commit_message >"
    override val description: String = "creates new commit to given world's repository, in current branch."

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 2)
            return createNotEnoughArgumentsResult(listOf(2), args.size)

        val world = args[0]
        val message = args.slice(1 until args.size).joinToString(" ")

        if (!isValidWorld(world) && world != "all")
            return createUnknownWorldResult(world)

        val targets = worldsWithAll(world).toMutableList()

        if (targets.size == 1) {
            val repo = parent.repositories[world]
                ?: return CommandExecuteResult(false, "$r'$world' $MESSAGE_NO_REPOSITORY\n$y$SUGGEST_NO_REPOSITORY")
            if (repo.refDatabase.findRef("HEAD").target.name == "HEAD")
                return CommandExecuteResult(false, "$r'$world' $MESSAGE_DETACHED_HEAD\n$y$SUGGEST_DETACHED_HEAD")
        } else {
            targets.removeAll(targets.filter { parent.repositories[it] == null })
            targets.filter {
                val repo = parent.repositories[it]
                repo != null && repo.refDatabase.findRef("HEAD").target.name == "HEAD"
            }.also { detached ->
                if (detached.isEmpty()) return@also

                val joinedTargets = targets.joinToString(", ") { "'$it'" }
                return CommandExecuteResult(false, "$r$joinedTargets $MESSAGE_DETACHED_HEAD\n$y$SUGGEST_DETACHED_HEAD")
            }
        }

        if (targets.size == 0)
            return CommandExecuteResult(true, "there are no repositories exists. nothing happened.")

        targets.forEach { IOWorker.addToVersionControl(parent, it) }

        return try {
            val hashes = mutableListOf<String>()

            targets.forEach {
                sendTitle("committing '$it' world")

                val git = Git(parent.repositories[it]!!)

                git.add().addFilepattern(".").call()

                val commit = git.commit()
                    .setMessage(message)
                    .setCommitter(PersonIdent("pixel-craft", "pixel.craft@hoonkun.kiwi"))
                    .call()

                hashes.add(commit.name.substring(0 until 7))
            }

            val committed = targets.joinToString(", ") { "$w$it(${parent.branch[it]})$g" }
            val resultHashes = hashes.joinToString(", ") { "$w$it$g" }

            CommandExecuteResult(true, "${g}successfully committed world [$committed] with hash [$resultHashes]")
        } catch (exception: GitAPIException) {
            createGitApiFailedResult("commit", exception)
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys.apply { add("all") }
            else -> ARGS_LIST_EMPTY
        }
    }

}