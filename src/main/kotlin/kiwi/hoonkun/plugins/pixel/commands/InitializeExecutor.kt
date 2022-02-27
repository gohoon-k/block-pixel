package kiwi.hoonkun.plugins.pixel.commands

import kiwi.hoonkun.plugins.pixel.Entry
import org.bukkit.command.CommandSender
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.*

class InitializeExecutor: Executor() {

    override fun exec(sender: CommandSender?, args: List<String>): Boolean {
        val processBuilder = ProcessBuilder("git", "init")
        processBuilder.directory(Entry.versionedFolder!!)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        val stream = process.inputStream

        val out = StringBuilder()
        val reader = BufferedReader(InputStreamReader(stream))

        do {
            val line = reader.readLine()
            out.append("$line\n")
        } while (line != null)

        val exitCode = process.waitFor()
        if (exitCode == 0) {
            val message = "successfully init local git repository!"

            if (sender != null) sender.sendMessage(message)
            else println(message)
        } else {
            val logFolder = Entry.logFolder!!
            if (!logFolder.exists()) logFolder.mkdirs()

            val fileName = "${Calendar.getInstance().getFormattedString()}_${args.joinToString("_")}.log"
            val logFile = File("${logFolder.absolutePath}/$fileName")
            logFile.createNewFile()
            logFile.writeBytes(out.toString().toByteArray())

            val message = "failed to init local git repository, full logs are in /plugins/pixel/logs/$fileName"

            if (sender != null) sender.sendMessage(message)
            else println(message)
        }
        return true
    }

    override fun autoComplete(args: List<String>): MutableList<String> {
        return mutableListOf()
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