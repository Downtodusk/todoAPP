package com.example.todo.diagnostics

import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap

/** Deliberately omits messages, thread names, file names, suppressed errors and arbitrary extras. */
object CrashReport {
    private val symbol = Regex("[A-Za-z0-9_.$<>-]{1,180}")
    private fun safeSymbol(value: String) = value.takeIf { symbol.matches(it) } ?: "unknown"
    fun render(error: Throwable, version: String, sdk: Int, now: Instant = Instant.now()): String = buildString {
        appendLine("Todo diagnostic v1")
        appendLine("time=$now app=${safeSymbol(version)} sdk=$sdk")
        appendLine("Exception messages and user data are intentionally excluded.")
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth++ < 6 && seen.add(cause)) {
            appendLine("exception=${safeSymbol(cause.javaClass.name)}")
            cause.stackTrace.take(24).forEach { frame ->
                appendLine("  at ${safeSymbol(frame.className)}.${safeSymbol(frame.methodName)}:${frame.lineNumber.coerceAtLeast(-1)}")
            }
            cause = cause.cause
        }
    }.take(24_576)
}
