package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.WorldAnvil
import kiwi.hoonkun.plugins.pixel.utils.ChatUtils.Companion.removeChatColor
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import kiwi.hoonkun.plugins.pixel.worker.MergeWorker
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toWorldAnvilFormat
import kiwi.hoonkun.plugins.pixel.worker.WorldLightUpdater
import kiwi.hoonkun.plugins.pixel.worker.WorldLoader
import kotlinx.coroutines.delay
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter
import kotlin.coroutines.cancellation.CancellationException


class MergeExecutor(parent: Entry): Executor(parent) {

    companion object {

        val FIRST_ARGS_LIST_WHEN_MERGING = mutableListOf("abort")

        val SECOND_ARGS_LIST = mutableListOf<String>()
        val THIRD_ARGS_LIST = mutableListOf("keep", "replace")

        const val IDLE = -1

        const val COMPUTING = 1
        const val RELOADING_WORLDS = -2
        const val APPLYING_LIGHTS = -3
        const val COMMITTING = -4

        val RESULT_INVALID_MERGE_MODE =
            CommandExecuteResult(false, "invalid merge mode. only 'keep' or 'replace' are supported.")

        val RESULT_DETACHED_HEAD =
            CommandExecuteResult(false, "$MESSAGE_DETACHED_HEAD\njust create new branch here, before merge.")

        val RESULT_FAILED =
            CommandExecuteResult(false, "failed to merge because of internal exception.")

        val RESULT_ABORT_SUCCESS =
            CommandExecuteResult(true, "merge operation is successfully aborted.")

        val RESULT_ABORT_SUCCESS_SILENT =
            CommandExecuteResult(true, "dummy. silent message.")

        val RESULT_ABORT_NOT_MERGING =
            CommandExecuteResult(false, "merger is not merging any commits")

        val RESULT_ABORT_LIGHT_UPDATING =
            CommandExecuteResult(false, "cannot abort merge operation while updating lights of world")

        val RESULT_ABORT_WORLD_LOADING =
            CommandExecuteResult(false, "cannot abort merge operation while world is loading")

        val RESULT_ABORT_COMMITTING =
            CommandExecuteResult(false, "cannot abort merge operation while committing merged data into local repository")

        val RESULT_ABORT_IMPOSSIBLE =
            CommandExecuteResult(false, "DODGE!!")

    }

    var state = IDLE

    override val usage: String = "merge < world > < branch | commit > < mode > < committed >"
    override val description: String = "merge another branch into current branch using specified merge mode"

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args[0] == "abort")
            return abort()

        if (args.size < 4)
            return createNotEnoughArgumentsResult(listOf(4), args.size)

        val world = args[0]
        val sourceArg = args[1]
        val mode = when (args[2]) {
            "keep" -> MergeWorker.Companion.MergeMode.KEEP
            "replace" -> MergeWorker.Companion.MergeMode.REPLACE
            else -> return RESULT_INVALID_MERGE_MODE
        }
        val commitConfirm = args[3]

        if (!isValidWorld(world))
            return createUnknownWorldResult(world)

        if (commitConfirm != "true")
            return RESULT_UNCOMMITTED

        val repo = parent.repositories[world] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        val head = repo.refDatabase.findRef("HEAD")
        if (head.target.name == "HEAD")
            return RESULT_DETACHED_HEAD

        val intoBranch = repo.branch ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        try {
            val message = merge(repo, sourceArg, world, mode)

            if (message != null) {
                sendTitle("finished merging, reloading world...")

                IOWorker.replaceFromVersionControl(parent, world, needsUnload = false)
            }

            state = IDLE

            return if (message != null)
                CommandExecuteResult(true, message)
            else
                RESULT_ABORT_SUCCESS
        } catch (exception: UnknownWorldException) {
            return createUnknownWorldResult(exception)
        } catch (e: Exception) {
            e.printStackTrace()

            Git(repo).checkout().setName(intoBranch).call()

            if (e is MergeException)
                return CommandExecuteResult(false, e.message!!)

            return RESULT_FAILED
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        if (state > 0 && args.size == 1) {
            return FIRST_ARGS_LIST_WHEN_MERGING
        }
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
            3 -> THIRD_ARGS_LIST
            4 -> ARGS_LIST_COMMIT_CONFIRM
            else -> ARGS_LIST_EMPTY
        }
    }

    private suspend fun merge(
        repo: Repository,
        source: String,
        world: String,
        mode: MergeWorker.Companion.MergeMode
    ): String? {
        val git = Git(repo)

        val intoBranch = repo.branch
        val intoCommitIsOnlyCommit = git.log().call().toList().size == 1

        val (into, intoCommit) = readAnvil(world, repo)
        val (from, fromCommit) = readAnvil(world, repo, source)

        if (intoCommit.name == fromCommit.name) throw InvalidMergeOperationException()

        val commitWalk = RevWalk(git.repository)

        val base = RevWalk(git.repository)
            .apply {
                revFilter = RevFilter.MERGE_BASE
                markStart(listOf(commitWalk.lookupCommit(intoCommit), commitWalk.lookupCommit(fromCommit)))
            }
            .next()
            ?: if(intoCommitIsOnlyCommit) intoCommit else throw NullMergeBaseException()

        git.checkout().setName(base.name).call()

        val ancestor = IOWorker.repositoryWorldNBTs(world)

        git.checkout().setName(intoBranch).call()

        try {
            state = COMPUTING

            sendTitle("start merging '$world'...")
            delay(500)

            val clientRegions = MergeWorker.merge(parent.job, from, into, ancestor, mode)
                .toWorldAnvilFormat()

            state = RELOADING_WORLDS
            WorldLoader.movePlayersTo(parent, world)
            WorldLoader.unload(parent, world)
            IOWorker.writeWorldAnvilToClient(clientRegions, world)

            state = APPLYING_LIGHTS
            WorldLoader.load(parent, world)
            WorldLightUpdater.updateLights(parent, world)
        } catch (exception: CancellationException) {
            state = RELOADING_WORLDS
            sendTitle("aborting merge operation...")
            return null
        }

        state = RELOADING_WORLDS

        sendTitle("light updated finished, reloading world...")
        IOWorker.addToVersionControl(parent, world, needsLoad = false)

        state = COMMITTING

        val actualSource =
            if (fromCommit.name.startsWith(source)) source
            else "$source(${fromCommit.name.substring(0 until 7)})"

        val message = "'$w$actualSource$g' into '$w$intoBranch$g' of $w$world$g"

        git.add().addFilepattern(".").call()

        git.commit()
            .setMessage("merged ${message.removeChatColor()}")
            .setCommitter(PersonIdent("pixel-craft", "pixel.craft@hoonkun.kiwi"))
            .call()

        return "${g}committed successful merge of $message"
    }

    private fun abort(): CommandExecuteResult {
        return if (state > 0) {
            parent.job?.cancel()
            RESULT_ABORT_SUCCESS_SILENT
        } else {
            when (state) {
                IDLE -> RESULT_ABORT_NOT_MERGING
                APPLYING_LIGHTS -> RESULT_ABORT_LIGHT_UPDATING
                RELOADING_WORLDS -> RESULT_ABORT_WORLD_LOADING
                COMMITTING -> RESULT_ABORT_COMMITTING
                else -> RESULT_ABORT_IMPOSSIBLE
            }
        }
    }

    private fun readAnvil(world: String, repo: Repository, checkout: String? = null): Pair<WorldAnvil, ObjectId> {
        val git = Git(repo)

        if (checkout != null) {
            try {
                git.checkout().setName(checkout).call()
            } catch (e: GitAPIException) {
                throw UnknownSourceException(checkout)
            }
        }

        return if (repo.refDatabase.findRef("HEAD").target.name == "HEAD") {
            Pair(IOWorker.repositoryWorldNBTs(world), repo.resolve(checkout))
        } else {
            val commits = git.log().setMaxCount(1).call().toList()
            val commit =
                if (commits.isNotEmpty()) commits[0]
                else throw NoValidCommitsException()

            sendTitle("reading regions...")
            Pair(IOWorker.repositoryWorldNBTs(world), commit.id)
        }
    }

    open class MergeException(m: String): Exception(m)

    class UnknownSourceException(source: String): MergeException("merge failed, unknown source with given name '$source'")

    class NullMergeBaseException: MergeException("merge failed, cannot find valid merge base.")

    class InvalidMergeOperationException: MergeException("invalid merge operation. source commit equals with current commit.")

    class NoValidCommitsException: MergeException("merge failed, there are no commits exists.\ndid you make any commits?")

}