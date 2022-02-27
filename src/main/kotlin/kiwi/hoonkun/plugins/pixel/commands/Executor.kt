package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*

abstract class Executor {

    abstract fun exec(sender: CommandSender?, args: List<String>): Boolean

    abstract fun autoComplete(args: List<String>): MutableList<String>

    fun returnMessage(sender: CommandSender?, message: String): Boolean {
        if (sender != null) sender.sendMessage(message)
        else println(message)
        return true
    }

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

    fun Pair<Int, String>.handle(sender: CommandSender?, command: String, successMessage: String, failedMessage: String): Boolean {
        val exitCode = first
        val out = second

        return if (exitCode == 0) {
            returnMessage(sender, successMessage)
        } else {
            val logFolder = Entry.logFolder!!
            if (!logFolder.exists()) logFolder.mkdirs()

            val fileName = "${Calendar.getInstance().getFormattedString()}_$command.log"
            val logFile = File("${logFolder.absolutePath}/$fileName")
            logFile.createNewFile()
            logFile.writeBytes(out.toByteArray())

            returnMessage(sender, failedMessage)
            false
        }
    }

    private fun Calendar.getFormattedString(): String {
        val year = get(Calendar.YEAR).toString()
        val month = get(Calendar.MONTH).toString().format("%02d")
        val day = get(Calendar.DAY_OF_MONTH).toString().format("%02d")
        val hour = get(Calendar.HOUR).toString().format("%02d")
        val minute = get(Calendar.MINUTE).toString().format("%02d")
        val second = get(Calendar.SECOND).toString().format("%02d")
        return "$year$month$day$hour$minute$second"
    }

}