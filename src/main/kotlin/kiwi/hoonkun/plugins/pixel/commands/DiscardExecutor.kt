package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.PixelWorker
import org.bukkit.command.CommandSender

class DiscardExecutor(private val plugin: Entry): Executor() {

    override suspend fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        if (args.isEmpty())
            return CommandExecuteResult(false, "missing argument. discard target must be specified.")

        PixelWorker.replaceFromVersionControl(plugin, dimensions(args[0]))
        return CommandExecuteResult(true, "${g}successfully discard uncommitted changes")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return when (args.size) {
            1 -> COMPLETE_LIST_DIMENSIONS
            else -> COMPLETE_LIST_EMPTY
        }
    }

}