package kiwi.hoonkun.plugins.pixel.utils

import org.bukkit.ChatColor

class ChatUtils {

    companion object {

        private val widths = mapOf(
            'a' to 5, 'b' to 5, 'c' to 5, 'd' to 5, 'e' to 5, 'f' to 4, 'g' to 5, 'h' to 5, 'i' to 1, 'j' to 5, 'k' to 4, 'l' to 2, 'm' to 5,
            'n' to 5, 'o' to 5, 'p' to 5, 'q' to 5, 'r' to 5, 's' to 5, 't' to 3, 'u' to 4, 'v' to 5, 'w' to 5, 'x' to 5, 'y' to 5, 'z' to 5,
            'A' to 5, 'B' to 5, 'C' to 5, 'D' to 5, 'E' to 5, 'F' to 5, 'G' to 5, 'H' to 5, 'I' to 3, 'J' to 5, 'K' to 5, 'L' to 5, 'M' to 5,
            'N' to 5, 'O' to 5, 'P' to 5, 'Q' to 5, 'R' to 5, 'S' to 5, 'T' to 5, 'U' to 5, 'V' to 5, 'W' to 5, 'X' to 5, 'Y' to 5, 'Z' to 5,
            '1' to 5, '2' to 5, '3' to 5, '4' to 5, '5' to 5, '6' to 5, '7' to 5, '8' to 5, '9' to 5, '0' to 5, '"' to 3, '\'' to 1, '/' to 5,
            '\\' to 5, '-' to 4, '_' to 4, ' ' to 3, '(' to 3, ')' to 3, '[' to 2, ']' to 2, '<' to 4, '>' to 4, '.' to 1, ':' to 1, ';' to 1, '|' to 1
        )

        private const val letterSpacing = 1

        fun String.ellipsizeChat(): String {
            var result = ""
            var width = 0
            var ellipsized = false
            var isColorValue = false
            for (index in indices) {
                if (this[index] == '§') {
                    isColorValue = true
                    result += this[index]
                    continue
                }
                if (isColorValue) {
                    isColorValue = false
                    result += this[index]
                    continue
                }
                width += widths[this[index]] ?: 5
                width += letterSpacing
                result += this[index]
                if (width >= 300) {
                    ellipsized = true
                    break
                }
            }
            return "$result${if (ellipsized) "${ChatColor.GRAY}..." else ""}"
        }

    }

}