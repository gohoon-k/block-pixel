package kiwi.hoonkun.plugins.pixel.utils

class TextWidthUtils {

    companion object {

        private val widths = mapOf(
            'a' to 4, 'b' to 4, 'c' to 4, 'd' to 4, 'e' to 4, 'f' to 3, 'g' to 4, 'h' to 4, 'i' to 1, 'j' to 4, 'k' to 4, 'l' to 2, 'm' to 5,
            'n' to 4, 'o' to 4, 'p' to 4, 'q' to 4, 'r' to 4, 's' to 4, 't' to 3, 'u' to 4, 'v' to 5, 'w' to 5, 'x' to 5, 'y' to 4, 'z' to 5,
            'A' to 4, 'B' to 4, 'C' to 4, 'D' to 4, 'E' to 4, 'F' to 4, 'G' to 4, 'H' to 4, 'I' to 3, 'J' to 4, 'K' to 4, 'L' to 4, 'M' to 5,
            'N' to 5, 'O' to 4, 'P' to 4, 'Q' to 4, 'R' to 4, 'S' to 4, 'T' to 5, 'U' to 4, 'V' to 5, 'W' to 5, 'X' to 5, 'Y' to 5, 'Z' to 5,
            '1' to 5, '2' to 5, '3' to 5, '4' to 5, '5' to 5, '6' to 5, '7' to 5, '8' to 5, '9' to 5, '0' to 5, '"' to 3, '\'' to 1, '/' to 5, '\\' to 5,
            '-' to 4, '_' to 4, ' ' to 2
        )

        private const val letterSpacing = 1

        fun String.ellipsizeChat(): String {
            var result = ""
            var width = 0
            var ellipsized = false
            for (index in indices) {
                width += widths[this[index]] ?: 5
                width += letterSpacing
                result += this[index]
                if (width >= 320) {
                    ellipsized = true
                    break
                }
            }
            return "$result${if (ellipsized) "..." else ""}"
        }

    }

}