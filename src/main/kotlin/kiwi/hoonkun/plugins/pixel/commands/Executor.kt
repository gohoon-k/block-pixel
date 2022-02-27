package kiwi.hoonkun.plugins.pixel.commands

import org.bukkit.command.CommandSender
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

abstract class Executor {

    data class CommandExecuteResult(
        val success: Boolean,
        val message: String
    )

    abstract fun exec(sender: CommandSender?, args: List<String>): CommandExecuteResult

    abstract fun autoComplete(args: List<String>): MutableList<String>

    fun spawn(command: List<String>, workingDirectory: File): Pair<Int, String> {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(workingDirectory)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        val stream = process.inputStream

        val out = StringBuilder()
        val reader = BufferedReader(InputStreamReader(stream))

        do {
            val line = reader.readLine()
            out.append("$line\n")
        } while (line != null)

        return Pair(process.waitFor(), out.toString())
    }

    fun Pair<Int, String>.handle(successMessage: String, failedMessage: String): CommandExecuteResult {
        val exitCode = first
        val out = second

        return if (exitCode == 0) {
            CommandExecuteResult(true, successMessage)
        } else {
            CommandExecuteResult(false, "$failedMessage\ngit says:\n$out")
        }
    }

}