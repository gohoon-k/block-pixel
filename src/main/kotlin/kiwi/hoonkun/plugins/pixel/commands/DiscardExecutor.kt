package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender

class DiscardExecutor(private val plugin: Entry): Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "missing argument. discard target must be specified.")

        val dimensions = if (args[0] == "all") listOf("overworld", "nether", "the_end") else listOf(args[0])

        val writeResult = WriteWorker.versioned2client(plugin, dimensions)

        if (writeResult != WriteWorker.RESULT_OK) return CommandExecuteResult(false, writeResult)
        return CommandExecuteResult(true, "successfully discard uncommitted changes")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_DIMENSIONS
            else -> COMPLETE_LIST_EMPTY
        }
    }

}