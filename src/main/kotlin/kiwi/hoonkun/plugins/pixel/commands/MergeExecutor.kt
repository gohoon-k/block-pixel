package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import kiwi.hoonkun.plugins.pixel.worker.MergeWorker
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toWorldAnvilFormat
import kiwi.hoonkun.plugins.pixel.worker.WorldLightUpdater
import kiwi.hoonkun.plugins.pixel.worker.WorldLoader
import kotlinx.coroutines.delay
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
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

        const val MERGING = 1
        const val RELOADING_WORLDS = -2
        const val APPLYING_LIGHTS = -3
        const val COMMITTING = -4

        val RESULT_NO_MERGE_SOURCE =
            CommandExecuteResult(false, "missing arguments. merge source must be specified.")

        val RESULT_NO_MERGE_MODE =
            CommandExecuteResult(false, "missing arguments. merge mode must be specified.")

        val RESULT_INVALID_MERGE_MODE =
            CommandExecuteResult(false, "invalid merge mode. only 'keep' or 'replace' are supported.")

        val RESULT_DETACHED_HEAD =
            CommandExecuteResult(false, "$MESSAGE_DETACHED_HEAD\njust create new branch here, before merge.")

        val RESULT_CANCELED =
            CommandExecuteResult(true, "merge operation was canceled by operator.")

        val RESULT_FAILED =
            CommandExecuteResult(false, "failed to merge because of internal exception.")

        val RESULT_ABORT_SUCCESS =
            CommandExecuteResult(true, "merge operation is canceled by operator.")

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

    private var initialBranch: String? = null

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args[0] == "abort" && state > 0) {
            parent.job?.cancel()
            return RESULT_ABORT_SUCCESS
        } else if (args[0] == "abort" && state < 0) {
            return when (state) {
                IDLE -> RESULT_ABORT_NOT_MERGING
                APPLYING_LIGHTS -> RESULT_ABORT_LIGHT_UPDATING
                RELOADING_WORLDS -> RESULT_ABORT_WORLD_LOADING
                COMMITTING -> RESULT_ABORT_COMMITTING
                else -> RESULT_ABORT_IMPOSSIBLE
            }
        }

        if (args.size == 1)
            return RESULT_NO_MERGE_SOURCE

        val world = args[0]
        val sourceArg = args[1]

        if (args.size == 2)
            return RESULT_NO_MERGE_MODE

        val mode = when (args[2]) {
            "keep" -> MergeWorker.Companion.MergeMode.KEEP
            "replace" -> MergeWorker.Companion.MergeMode.REPLACE
            else -> return RESULT_INVALID_MERGE_MODE
        }

        if (!isValidWorld(world))
            return createUnknownWorldResult(world)

        val repo = parent.repositories[world] ?: return RESULT_REPOSITORY_NOT_INITIALIZED

        if (args.size == 3)
            return RESULT_NO_COMMIT_CONFIRM

        if (args[3] != "true")
            return RESULT_UNCOMMITTED

        val head = repo.refDatabase.findRef("HEAD")
        if (head.target.name == "HEAD")
            return RESULT_DETACHED_HEAD

        try {
            initialBranch = repo.branch ?: return RESULT_REPOSITORY_NOT_INITIALIZED

            val message = merge(repo, sourceArg, world, mode)

            if (message != null) {
                sendTitle("finished merging, reloading world...")

                IOWorker.replaceFromVersionControl(parent, world, needsUnload = false)

                WorldLoader.returnPlayersTo(parent, world)
            }

            state = IDLE

            return if (message != null)
                CommandExecuteResult(true, message)
            else
                RESULT_CANCELED
        } catch (exception: UnknownWorldException) {
            return createUnknownWorldResult(exception)
        } catch (e: Exception) {
            e.printStackTrace()

            val branch = initialBranch

            if (branch != null)
                Git(repo)
                    .checkout()
                    .setName(branch)
                    .call()

            initialBranch = null

            if (e is MergeException) {
                return CommandExecuteResult(false, e.message!!)
            }

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

        val intoCommits = git.log().setMaxCount(1).call().toList()
        val intoCommit =
            if (intoCommits.isNotEmpty()) intoCommits[0]
            else throw NoValidCommitsException()

        val intoCommitIsOnlyCommit = git.log().call().toList().size == 1

        sendTitle("reading current regions...")
        val into = IOWorker.repositoryWorldNBTs(world)

        try {
            git.checkout().setName(source).call()
        } catch (e: GitAPIException) {
            throw UnknownSourceException(source)
        }

        val fromCommits = git.log().setMaxCount(1).call().toList()
        val fromCommit =
            if (fromCommits.isNotEmpty()) fromCommits[0]
            else throw NoValidCommitsException()

        sendTitle("reading current regions...")
        val from = IOWorker.repositoryWorldNBTs(world)

        val commitLookup = RevWalk(git.repository)
        val intoC = commitLookup.lookupCommit(intoCommit.id)
        val fromC = commitLookup.lookupCommit(fromCommit.id)

        if (intoC.name == fromC.name) throw InvalidMergeOperationException()

        val walk = RevWalk(git.repository)
        walk.revFilter = RevFilter.MERGE_BASE
        walk.markStart(listOf(intoC, fromC))
        val mergeBase = walk.next() ?: if(intoCommitIsOnlyCommit) intoCommit else throw NullMergeBaseException()

        git.checkout().setName(mergeBase.name).call()

        sendTitle("reading merge-base regions...")
        val ancestor = IOWorker.repositoryWorldNBTs(world)

        val branch = initialBranch

        git.checkout()
            .setName(branch)
            .call()

        initialBranch = null

        try {
            state = MERGING

            sendTitle("start merging '$world'...")
            delay(500)

            val clientRegions = MergeWorker.merge(
                parent.job,
                from,
                into,
                ancestor,
                mode
            ).toWorldAnvilFormat()

            state = RELOADING_WORLDS
            WorldLoader.movePlayersTo(parent, world)
            WorldLoader.unload(parent, world)
            IOWorker.writeWorldAnvilToClient(clientRegions, world)
            WorldLoader.load(parent, world)
            state = APPLYING_LIGHTS
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
            if (fromC.name.startsWith(source)) source
            else "$source(${fromC.name.substring(0 until 7)})"

        val message = "'$w$actualSource$g' into '$w$branch$g' of $w$world$g"

        git.add()
            .addFilepattern(".")
            .call()

        git.commit()
            .setMessage("merged ${message.replace("$w", "").replace("$g", "")}")
            .setCommitter(PersonIdent(git.repository))
            .call()

        return "${g}committed successful merge of $message"
    }

    open class MergeException(m: String): Exception(m)

    class UnknownSourceException(source: String): MergeException("merge failed, unknown source with given name '$source'")

    class NullMergeBaseException: MergeException("merge failed, cannot find valid merge base.")

    class InvalidMergeOperationException: MergeException("invalid merge operation. source commit equals with current commit.")

    class NoValidCommitsException: MergeException("merge failed, there are no commits exists.\ndid you make any commits?")

}