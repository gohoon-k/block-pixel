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

        val COMPLETE_LIST_1 = mutableListOf("<branch>", "<commit_hash>")
        val COMPLETE_LIST_3 = mutableListOf("keep", "replace")
        val COMPLETE_LIST_4 = mutableListOf("<all_change_committed>")

    }

    private var initialBranch: String? = null

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.size < 3)
            return CommandExecuteResult(false, "missing arguments. target world, merge source, merge mode must be specified.")

        if (args.size < 4)
            return CommandExecuteResult(false, "you must specify that you have committed all uncommitted changes before merging.\nif yes, pass 'true' to last argument.")

        val dimensions = dimensions(args[0])
        val from = args[1]
        val mode = if (args[2] == "keep")
            RegionWorker.Companion.MergeMode.KEEP
        else if (args[2] == "replace")
            RegionWorker.Companion.MergeMode.REPLACE
        else return CommandExecuteResult(false, "invalid merge mode. only 'keep' or 'replace' are supported.")

        initialBranch = Entry.repository?.branch ?: return invalidRepositoryResult

        try {
            val message = merge(from, dimensions, mode)
            sendTitle("finished merging, reloading world...")
            PixelWorker.replaceFromVersionControl(plugin, dimensions)

            return CommandExecuteResult(true, message)
        } catch (e: Exception) {
            e.printStackTrace()

            if (initialBranch != null)
                Git(Entry.repository)
                    .checkout()
                    .setName(initialBranch)
                    .call()

            initialBranch = null

            if (e is MergeException) {
                return CommandExecuteResult(false, e.message!!)
            }

            return CommandExecuteResult(false, "failed to merge because of exception.")
        }
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_DIMENSIONS
            2 -> COMPLETE_LIST_1
            3 -> COMPLETE_LIST_3
            4 -> COMPLETE_LIST_4
            else -> COMPLETE_LIST_EMPTY
        }
    }

    private suspend fun merge(source: String, dimensions: List<String>, mode: RegionWorker.Companion.MergeMode): String {
        val git = Git(Entry.repository)

        val intoCommits = git.log().setMaxCount(1).call().toList()
        val intoCommit =
            if (intoCommits.isNotEmpty()) intoCommits[0]
            else throw NoValidCommitsException()

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
        val mergeBase = walk.next() ?: throw NullMergeBaseException()

        git.checkout().setName(mergeBase.name).call()

        sendTitle("reading merge-base regions...")
        val ancestor = PixelWorker.read(dimensions)

        val branch = initialBranch

        git.checkout()
            .setName(branch)
            .call()

        initialBranch = null

        dimensions.forEachIndexed { index, dimension ->
            sendTitle("start merging '$dimension'...")
            delay(2000)
            RegionWorker.merge(from[index], into[index], ancestor[index], mode).toClientRegions().write(dimension)
            sendTitle("merging '$dimension' finished.")
        }

        val actualSource =
            if (fromC.name.startsWith(source)) source
            else "$source(${fromC.name.substring(0 until 7)})"

        val message = if (dimensions.size != 1) {
            "all dimensions from '$actualSource' into '$branch'"
        } else {
            "${dimensions[0]} from '$actualSource' into '$branch'"
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