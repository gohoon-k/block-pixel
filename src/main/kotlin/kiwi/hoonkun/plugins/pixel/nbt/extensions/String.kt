package kiwi.hoonkun.plugins.pixel.nbt.extensions

fun String.indent() = split("\n").joinToString("\n") { "  $it" }