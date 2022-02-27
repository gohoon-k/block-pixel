package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender

class DiscardExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        WriteWorker.versioned2client(listOf("overworld", "nether", "the_end"))
        return CommandExecuteResult(true, "successfully discard uncommitted changes")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}