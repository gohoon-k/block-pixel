package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import kiwi.hoonkun.plugins.pixel.worker.MergeWorker
import kiwi.hoonkun.plugins.pixel.worker.MinecraftAnvilWorker.Companion.toWorldAnvilFormat
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


class MergeExecutor(private val plugin: Entry): Executor() {

    companion object {

        val FIRST_ARGS_LIST_WHEN_MERGING = mutableListOf("abort")

        val SECOND_ARGS_LIST = mutableListOf("< branch_name >", "< commit_hash >")
        val THIRD_ARGS_LIST = mutableListOf("keep", "replace")

        const val IDLE = -1

        const val MERGING = 1
        const val RELOADING_WORLDS = -2
        const val APPLYING_LIGHTS = -3
        const val COMMITTING = -4

    }

    var state = IDLE

    private var initialBranch: String? = null

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val repo = Entry.repository ?: return invalidRepositoryResult

        if (args.size < 3)
            return CommandExecuteResult(false, "missing arguments. target world, merge source, merge mode must be specified.")

        if (args.size < 4)
            return CommandExecuteResult(false, "you must specify that you have committed all uncommitted changes before merging.\nif yes, pass 'true' to last argument.")

        if (args[3] != "true")
            return uncommittedChangesResult

        val head = repo.refDatabase.findRef("HEAD")
        if (head.target.name == "HEAD")
            return CommandExecuteResult(false, "it seems that head is detached from any other branches.\njust create new branch here, before merge.")

        try {
            val dimensions = dimensions(args[0])
            val from = args[1]
            val mode = if (args[2] == "keep")
                MergeWorker.Companion.MergeMode.KEEP
            else if (args[2] == "replace")
                MergeWorker.Companion.MergeMode.REPLACE
            else return CommandExecuteResult(false, "invalid merge mode. only 'keep' or 'replace' are supported.")

            initialBranch = Entry.repository?.branch ?: return invalidRepositoryResult

            val message = merge(repo, from, dimensions, mode)

            if (message != null) {
                sendTitle("finished merging, reloading world...")

                IOWorker.replaceFromVersionControl(plugin, dimensions, needsUnload = false)

                dimensions.forEach {
                    WorldLoader.returnPlayersTo(plugin, it)
                }
            }

            state = IDLE

            return if (message != null)
                CommandExecuteResult(true, message)
            else
                CommandExecuteResult(true, "merge operation was canceled by operator.")
        } catch (exception: UnknownDimensionException) {
            return createDimensionExceptionResult(exception)
        } catch (e: Exception) {
            e.printStackTrace()

            val branch = initialBranch

            if (branch != null)
                Git(Entry.repository)
                    .checkout()
                    .setName(branch)
                    .call()

            initialBranch = null

            if (e is MergeException) {
                return CommandExecuteResult(false, e.message!!)
            }

            return CommandExecuteResult(false, "failed to merge because of internal exception.")
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        if (state > 0 && args.size == 1) {
            return FIRST_ARGS_LIST_WHEN_MERGING
        }
        return when (args.size) {
            1 -> ARGS_LIST_DIMENSIONS
            2 -> SECOND_ARGS_LIST
            3 -> THIRD_ARGS_LIST
            4 -> ARGS_LIST_COMMIT_CONFIRM
            else -> ARGS_LIST_EMPTY
        }
    }

    private suspend fun merge(
        repo: Repository,
        source: String,
        dimensions: List<String>,
        mode: MergeWorker.Companion.MergeMode
    ): String? {
        val git = Git(repo)

        val intoCommits = git.log().setMaxCount(1).call().toList()
        val intoCommit =
            if (intoCommits.isNotEmpty()) intoCommits[0]
            else throw NoValidCommitsException()

        val intoCommitIsOnlyCommit = git.log().call().toList().size == 1

        sendTitle("reading current regions...")
        val into = IOWorker.repositoryWorldNBTs(dimensions)

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
        val from = IOWorker.repositoryWorldNBTs(dimensions)

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
        val ancestor = IOWorker.repositoryWorldNBTs(dimensions)

        val branch = initialBranch

        git.checkout()
            .setName(branch)
            .call()

        initialBranch = null

        try {
            state = MERGING

            val mergedDimensions = dimensions.associateWith { dimension ->
                sendTitle("start merging '$dimension'...")
                delay(1000)
                val clientRegions = MergeWorker.merge(
                    from.getValue(dimension),
                    into.getValue(dimension),
                    ancestor.getValue(dimension),
                    mode
                ).toWorldAnvilFormat()
                sendTitle("merging '$dimension' finished.")
                clientRegions
            }

            mergedDimensions.forEach { (dimension, regions) ->
                state = RELOADING_WORLDS
                WorldLoader.movePlayersTo(plugin, dimension)
                WorldLoader.unload(plugin, dimension)
                IOWorker.writeWorldAnvilToClient(regions, dimension)
                WorldLoader.load(plugin, dimension)
                state = APPLYING_LIGHTS
                WorldLoader.updateLights(plugin, dimension)
            }
        } catch (exception: CancellationException) {
            state = RELOADING_WORLDS
            sendTitle("aborting merge operation...")
            return null
        }

        state = RELOADING_WORLDS

        sendTitle("light updated finished, reloading world...")
        IOWorker.addToVersionControl(plugin, dimensions, needsLoad = false)

        state = COMMITTING

        val actualSource =
            if (fromC.name.startsWith(source)) source
            else "$source(${fromC.name.substring(0 until 7)})"

        val message = if (dimensions.size != 1) {
            "'$w$actualSource$g' into '$w$branch$g' of ${w}all dimensions$g"
        } else {
            "'$w$actualSource$g' into '$w$branch$g' of $w${dimensions[0]}$g"
        }

        git.add()
            .addFilepattern(".")
            .call()

        git.commit()
            .setMessage("merged ${message.replace("$w", "").replace("$g", "")}")
            .setCommitter(PersonIdent(git.repository))
            .call()

        return "${g}committed successful merge of $message"
    }

    open class MergeException(val m: String): Exception(m)

    class UnknownSourceException(source: String): MergeException("merge failed, unknown source with given name '$source'")

    class NullMergeBaseException: MergeException("merge failed, cannot find valid merge base.")

    class InvalidMergeOperationException: MergeException("invalid merge operation. source commit equals with into commit.")

    class NoValidCommitsException: MergeException("merge failed, there are no commits exists.\ndid you make any commits?")

}