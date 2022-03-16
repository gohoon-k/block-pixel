package kiwi.hoonkun.plugins.pixel.utils

import org.bukkit.ChatColor
import kotlin.math.ceil

class ChatUtils {

    companion object {

        private val widths = mapOf(
            'a' to 5f, 'b' to 5f, 'c' to 5f, 'd' to 5f, 'e' to 5f, 'f' to 4f, 'g' to 5f, 'h' to 5f, 'i' to 1f, 'j' to 5f, 'k' to 4f, 'l' to 2f, 'm' to 5f,
            'n' to 5f, 'o' to 5f, 'p' to 5f, 'q' to 5f, 'r' to 5f, 's' to 5f, 't' to 3f, 'u' to 4f, 'v' to 5f, 'w' to 5f, 'x' to 5f, 'y' to 5f, 'z' to 5f,
            'A' to 5f, 'B' to 5f, 'C' to 5f, 'D' to 5f, 'E' to 5f, 'F' to 5f, 'G' to 5f, 'H' to 5f, 'I' to 3f, 'J' to 5f, 'K' to 5f, 'L' to 5f, 'M' to 5f,
            'N' to 5f, 'O' to 5f, 'P' to 5f, 'Q' to 5f, 'R' to 5f, 'S' to 5f, 'T' to 5f, 'U' to 5f, 'V' to 5f, 'W' to 5f, 'X' to 5f, 'Y' to 5f, 'Z' to 5f,
            '1' to 5f, '2' to 5f, '3' to 5f, '4' to 5f, '5' to 5f, '6' to 5f, '7' to 5f, '8' to 5f, '9' to 5f, '0' to 5f, '"' to 3f, '\'' to 1f, '/' to 5f,
            '\\' to 5f, '-' to 4f, '_' to 4f, ' ' to 3f, '(' to 3f, ')' to 3f, '[' to 2f, ']' to 2f, '<' to 4f, '>' to 4f, '.' to 1f, ':' to 1f, ';' to 1f, '|' to 1f
        )

        private const val maxWidth = 300

        private const val defaultWidth = 20f / 3f

        private const val letterSpacing = 1f

        fun String.ellipsizeChat(): String {
            var result = ""
            var width = 0f
            var ellipsized = false
            for (index in indices) {
                if (this[index] == '§') {
                    result += this[index]
                    width -= (widths[this[index + 1]] ?: defaultWidth) + letterSpacing
                    continue
                }

                width += (widths[this[index]] ?: defaultWidth) + letterSpacing
                result += this[index]

                if (width >= maxWidth) {
                    ellipsized = true
                    break
                }
            }
            return "$result${if (ellipsized) "${ChatColor.GRAY}..." else ""}"
        }

        fun String.ellipsizeChatHead(): String {
            var result = ""
            var width = 0f
            var ellipsized = false
            var chatColorIndex = length
            for (index in length - 1 downTo 0) {
                if (this[index] == '§') {
                    width -= widths[result[0]] ?: defaultWidth
                    result = "${this[index]}$result"
                    chatColorIndex = index
                    continue
                }

                width += (widths[this[index]] ?: defaultWidth) + letterSpacing
                result = "${this[index]}$result"

                if (index > 1 && this[index - 1] != '§' && width >= 300) {
                    ellipsized = true
                    break
                }
            }

            val sub = substring(0 until chatColorIndex)
            val lastColorIndex = sub.lastIndexOf("§")
            val lastColor = if (lastColorIndex != -1)
                substring(lastColorIndex, lastColorIndex + 2)
            else ""

            return "${if (ellipsized) "${ChatColor.GRAY}...${ChatColor.RESET}" else ""}$lastColor$result"
        }

        fun String.appendRight(that: String): String {
            val thatWidth = that.removeChatColor().sumOf { ((widths[it] ?: defaultWidth) + letterSpacing).toDouble() }.toFloat()
            val thisWidth = removeChatColor().sumOf { ((widths[it] ?: defaultWidth) + letterSpacing).toDouble() }.toFloat()
            val remainingWidth = maxWidth - (thatWidth + thisWidth) + 6
            if (remainingWidth < 0) return "$this$that"
            var spacing = ""
            (0 until ceil(remainingWidth / (widths[' ']!! + letterSpacing)).toInt()).forEach { _ -> spacing += ' ' }
            return "$this$spacing$that"
        }

        fun String.removeChatColor(): String {
            return replace(Regex("§."), "")
        }

    }

}