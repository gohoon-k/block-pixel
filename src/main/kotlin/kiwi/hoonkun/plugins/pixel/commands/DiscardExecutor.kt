package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import kiwi.hoonkun.plugins.pixel.worker.WriteWorker
import org.bukkit.command.CommandSender

class DiscardExecutor(private val plugin: Entry): Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult {
        val writeResult = WriteWorker.versioned2client(plugin, listOf("overworld", "nether", "the_end"))
        if (writeResult != WriteWorker.RESULT_OK) return CommandExecuteResult(false, writeResult)
        return CommandExecuteResult(true, "successfully discard uncommitted changes")
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
    }

}