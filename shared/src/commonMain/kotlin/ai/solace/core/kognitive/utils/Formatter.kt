package ai.solace.core.kognitive.utils

class Formatter {
    private val builder = StringBuilder()

    fun format(fmt: String, vararg args: Any?): String {
        builder.clear() // Clear the builder at the start
        var localIndex = 0
        var argIndex = 0

        while (localIndex < fmt.length) {
            val c = fmt[localIndex]
            if (c != '%') {
                builder.append(c)
                localIndex++
                continue
            }

            if (localIndex + 1 >= fmt.length) {
                throw IllegalArgumentException("Incomplete format string")
            }

            localIndex++
            var width = -1
            var precision = -1

            // Parse width
            if (fmt[localIndex].isDigit()) {
                parseNumber(fmt, localIndex).also {
                    localIndex = it.first
                    width = it.second
                }
            }

            // Parse precision
            if (localIndex < fmt.length && fmt[localIndex] == '.') {
                localIndex++
                parseNumber(fmt, localIndex).also {
                    localIndex = it.first
                    precision = it.second
                }
            }

            if (localIndex >= fmt.length) {
                throw IllegalArgumentException("Incomplete format string")
            }

            val conversion = fmt[localIndex]

            if (argIndex >= args.size) {
                throw IllegalArgumentException("Too few arguments")
            }

            when (conversion) {
                'f' -> formatFloat(args[argIndex], width, precision)
                'd' -> formatInt(args[argIndex], width)
                's' -> formatString(args[argIndex], width)
                else -> throw IllegalArgumentException("Unknown format conversion: $conversion")
            }

            argIndex++
            localIndex++
        }
        return builder.toString()
    }

    private fun parseNumber(fmt: String, currentIndex: Int): Pair<Int, Int> {
        var num = 0
        var localIndex = currentIndex
        while (localIndex < fmt.length && fmt[localIndex].isDigit()) {
            num = num * 10 + (fmt[localIndex] - '0')
            localIndex++
        }
        return Pair(localIndex, num) // Return updated index and the parsed number
    }

    private fun formatFloat(arg: Any?, width: Int, precision: Int) {
        val value = when (arg) {
            is Float -> arg
            is Double -> arg.toFloat()
            else -> throw IllegalArgumentException("Expected float, got ${arg?.let { it::class.simpleName }}")
        }

        val actualPrecision = if (precision >= 0) precision else 6
        val numStr = String.format("%.${actualPrecision}f", value)

        padAndAppend(numStr, width)
    }

    private fun formatInt(arg: Any?, width: Int) {
        val value = when (arg) {
            is Int, is Long, is Short, is Byte -> arg.toString()
            else -> throw IllegalArgumentException("Expected integer, got ${arg?.let { it::class.simpleName }}")
        }

        padAndAppend(value, width)
    }

    private fun formatString(arg: Any?, width: Int) {
        val str = arg?.toString() ?: "null"
        padAndAppend(str, width)
    }

    private fun padAndAppend(value: String, width: Int) {
        if (width > 0 && value.length < width) {
            repeat(width - value.length) { builder.append(' ') }
        }
        builder.append(value)
    }

    companion object {
        fun format(format: String, vararg args: Any?): String {
            return Formatter().format(format, *args)
        }
    }
}

fun String.Companion.format(format: String, vararg args: Any?): String {
    return Formatter.format(format, *args)
}