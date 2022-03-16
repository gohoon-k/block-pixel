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
        private const val MESSAGE_CANNOT_AMEND = "cannot to amend commit"
        private const val MESSAGE_CANNOT_AMEND_ALL = "$MESSAGE_CANNOT_AMEND with 'all' world alias"
        private const val MESSAGE_CANNOT_AMEND_ALL_DIFF = "$MESSAGE_CANNOT_AMEND_ALL, because each worlds' latest commits have difference messages."
        private const val MESSAGE_CANNOT_AMEND_ALL_NO_COMMIT = "$MESSAGE_CANNOT_AMEND_ALL, because some repositories have no base commit to amend."
        private const val MESSAGE_CANNOT_AMEND_NO_COMMIT = "$MESSAGE_CANNOT_AMEND, because specified world repository has no base commit to amend."

        const val SUGGEST_NO_REPOSITORY = "please run '/pixel init' first."
        const val SUGGEST_DETACHED_HEAD = "please create branch using '/pixel branch' before commit."
        private const val SUGGEST_CANNOT_AMEND_ALL_DIFF = "try amend commit individually."
        private const val SUGGEST_CANNOT_AMEND_ALL_NO_COMMIT = "try amend commit individually or make base commit of amend commit."
        private const val SUGGEST_CANNOT_AMEND_NO_COMMIT = "try make a normal(base) commit first."

        val RESULT_NO_COMMIT_EXECUTED =
            CommandExecuteResult(true, "there are no repositories exists. nothing happened.")

        val RESULT_CANNOT_AMEND_ALL_DIFF =
            CommandExecuteResult(false, "$MESSAGE_CANNOT_AMEND_ALL_DIFF\n$y$SUGGEST_CANNOT_AMEND_ALL_DIFF")

        val RESULT_CANNOT_AMEND_ALL_NO_COMMIT =
            CommandExecuteResult(false, "$MESSAGE_CANNOT_AMEND_ALL_NO_COMMIT\n$y$SUGGEST_CANNOT_AMEND_ALL_NO_COMMIT")

        val RESULT_CANNOT_AMEND_NO_COMMIT =
            CommandExecuteResult(false, "$MESSAGE_CANNOT_AMEND_NO_COMMIT\n$y$SUGGEST_CANNOT_AMEND_NO_COMMIT")

        const val AMEND_ARG = "-amend"

        val SECOND_ARGS_LIST = mutableListOf(AMEND_ARG)

    }

    override val usage: String = "commit < world > [ \"$AMEND_ARG\" ] < commit_message >"
    override val description: String = "creates new commit to given world's repository, in current branch."

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 2)
            return createNotEnoughArgumentsResult(listOf(2), args.size)

        val world = args[0]
        var message = args.toMutableList()
            .apply { if (args[1] == AMEND_ARG) removeAt(1) }
            .let { it.slice(1 until it.size) }
            .joinToString(" ")

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
            return RESULT_NO_COMMIT_EXECUTED

        val amend = args[1] == AMEND_ARG

        if (targets.size != 1 && amend) {
            val messages = targets.map { parent.lastCommit[it]?.fullMessage }.toSet()
            if (messages.contains(null))
                return RESULT_CANNOT_AMEND_ALL_NO_COMMIT
            if (messages.size != 1)
                return RESULT_CANNOT_AMEND_ALL_DIFF
            if (message.isEmpty())
                message = messages.toList()[0]!!
        }

        if (targets.size == 1 && amend) {
            val oldMessage = parent.lastCommit[world]?.fullMessage ?: return RESULT_CANNOT_AMEND_NO_COMMIT
            if (message.isEmpty())
                message = oldMessage
        }

        targets.forEach { IOWorker.addToVersionControl(parent, it) }

        return try {
            val hashes = mutableListOf<String>()

            targets.forEach {
                sendTitle("committing '$it' world")

                val git = Git(parent.repositories[it]!!)

                git.add().addFilepattern(".").call()

                val commit = git.commit()
                    .setAmend(amend)
                    .setMessage(message)
                    .setCommitter(PersonIdent("pixel-craft", "pixel.craft@hoonkun.kiwi"))
                    .call()

                hashes.add(commit.name.substring(0 until 7))
            }

            val committed = targets.joinToString(", ") { "$w$it(${parent.branch[it]})$g" }
            val resultHashes = hashes.joinToString(", ") { "$w$it$g" }

            parent.updateLastCommit()

            CommandExecuteResult(true, "${g}successfully committed world [$committed] with hash [$resultHashes]")
        } catch (exception: GitAPIException) {
            createGitApiFailedResult("commit", exception)
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys.apply { add("all") }
            2 -> if (amendMessage(args) != null) SECOND_ARGS_LIST else ARGS_LIST_EMPTY
            3 -> {
                if (args[1] != AMEND_ARG) return ARGS_LIST_EMPTY

                val amendMessage = amendMessage(args)
                if (amendMessage != null) {
                    mutableListOf(amendMessage)
                } else {
                    ARGS_LIST_EMPTY
                }
            }
            else -> ARGS_LIST_EMPTY
        }
    }

    private fun amendMessage(args: List<String>): String? {
        val world = args[0]
        val targets = worldsWithAll(world).toMutableList()
        targets.removeAll(targets.filter { parent.repositories[it] == null })

        if (targets.size > 1) {
            val messages = targets.map { parent.lastCommit[it]?.fullMessage }.toSet()
            if (messages.contains(null))
                return null
            if (messages.size != 1)
                return null

            return messages.toList()[0]
        } else {
            return parent.lastCommit[world]?.fullMessage
        }
    }

}