package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.PixelWorker
import kiwi.hoonkun.plugins.pixel.worker.PixelWorker.Companion.write
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker
import kiwi.hoonkun.plugins.pixel.worker.RegionWorker.Companion.toClientRegions
import kotlinx.coroutines.delay
import org.bukkit.command.CommandSender
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.revwalk.filter.RevFilter


class MergeExecutor(private val plugin: Entry): Executor() {

    companion object {

        val COMPLETE_LIST_WHEN_MERGING = mutableListOf("abort")

        val COMPLETE_LIST_1 = mutableListOf("<branch>", "<commit_hash>")
        val COMPLETE_LIST_3 = mutableListOf("keep", "replace")

    }

    var canBeAborted = false

    private var initialBranch: String? = null

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 3)
            return CommandExecuteResult(false, "missing arguments. target world, merge source, merge mode must be specified.")

        if (args.size < 4)
            return CommandExecuteResult(false, "you must specify that you have committed all uncommitted changes before merging.\nif yes, pass 'true' to last argument.")

        if (args[3] != "true")
            return uncommittedChangesResult

        try {
            val dimensions = dimensions(args[0])
            val from = args[1]
            val mode = if (args[2] == "keep")
                RegionWorker.Companion.MergeMode.KEEP
            else if (args[2] == "replace")
                RegionWorker.Companion.MergeMode.REPLACE
            else return CommandExecuteResult(false, "invalid merge mode. only 'keep' or 'replace' are supported.")

            initialBranch = Entry.repository?.branch ?: return invalidRepositoryResult

            val message = merge(from, dimensions, mode)
            sendTitle("finished merging, reloading world...")
            PixelWorker.replaceFromVersionControl(plugin, dimensions)

            return CommandExecuteResult(true, message)
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
        if (canBeAborted && args.size == 1) {
            return COMPLETE_LIST_WHEN_MERGING
        }
        return when (args.size) {
            1 -> COMPLETE_LIST_DIMENSIONS
            2 -> COMPLETE_LIST_1
            3 -> COMPLETE_LIST_3
            4 -> COMPLETE_LIST_COMMITTED
            else -> COMPLETE_LIST_EMPTY
        }
    }

    private suspend fun merge(source: String, dimensions: List<String>, mode: RegionWorker.Companion.MergeMode): String {
        val git = Git(Entry.repository)

        val intoCommits = git.log().setMaxCount(1).call().toList()
        val intoCommit =
            if (intoCommits.isNotEmpty()) intoCommits[0]
            else throw NoValidCommitsException()

        val intoCommitIsOnlyCommit = git.log().call().toList().size == 1

        sendTitle("reading current regions...")
        val into = PixelWorker.read(dimensions)

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
        val from = PixelWorker.read(dimensions)

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
        val ancestor = PixelWorker.read(dimensions)

        val branch = initialBranch

        git.checkout()
            .setName(branch)
            .call()

        initialBranch = null

        canBeAborted = true

        dimensions.forEachIndexed { index, dimension ->
            sendTitle("start merging '$dimension'...")
            delay(1000)
            RegionWorker.merge(from[index], into[index], ancestor[index], mode).toClientRegions().write(dimension)
            sendTitle("merging '$dimension' finished.")
        }

        canBeAborted = false

        val actualSource =
            if (fromC.name.startsWith(source)) source
            else "$source(${fromC.name.substring(0 until 7)})"

        val message = if (dimensions.size != 1) {
            "'$actualSource' into '$branch' of all dimensions"
        } else {
            "'$actualSource' into '$branch' of ${dimensions[0]}"
        }

        git.add()
            .addFilepattern(".")
            .call()

        git.commit()
            .setMessage("merged $message")
            .setCommitter(PersonIdent(git.repository))
            .call()

        return "committed successful merge of $message"
    }

    open class MergeException(val m: String): Exception(m)

    class UnknownSourceException(source: String): MergeException("merge failed, unknown source with given name '$source'")

    class NullMergeBaseException: MergeException("merge failed, cannot find valid merge base.")

    class InvalidMergeOperationException: MergeException("invalid merge operation. source commit equals with into commit.")

    class NoValidCommitsException: MergeException("merge failed, there are no commits exists.\ndid you make any commits?")

}