package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.IOWorker
import org.bukkit.command.CommandSender

class DiscardExecutor(parent: Entry): Executor(parent) {

    companion object {
        val RESULT_SUCCESSFUL = CommandExecuteResult(true, "${g}successfully discard uncommitted changes")
    }

    override val usage: String = "discard < world >"
    override val description: String = "discards all uncommitted changes of given world."

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        try {
            IOWorker.replaceFromVersionControl(parent, args[0])
        } catch (exception: UnknownWorldException) {
            return createUnknownWorldResult(exception)
        }
        return RESULT_SUCCESSFUL
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> parent.repositoryKeys
            else -> ARGS_LIST_EMPTY
        }
    }

}