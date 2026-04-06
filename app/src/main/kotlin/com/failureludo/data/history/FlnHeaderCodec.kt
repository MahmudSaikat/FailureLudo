package com.failureludo.data.history

private val HEADER_REGEX = Regex("^\\[(\\w+)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\]$")

internal fun parseHeaderLine(line: String): Pair<String, String>? {
    val match = HEADER_REGEX.matchEntire(line) ?: return null
    val tag = match.groupValues[1]
    val value = decodeHeaderValue(match.groupValues[2])
    return tag to value
}

internal fun encodeHeaderValue(value: String): String {
    return buildString {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(ch)
            }
        }
    }
}

private fun decodeHeaderValue(raw: String): String {
    return buildString {
        var index = 0
        while (index < raw.length) {
            val ch = raw[index]
            if (ch == '\\' && index + 1 < raw.length) {
                append(raw[index + 1])
                index += 2
            } else {
                append(ch)
                index += 1
            }
        }
    }
}
