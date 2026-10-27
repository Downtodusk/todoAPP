package com.example.todo.diagnostics

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.nio.file.Files

class CrashReportTest {
    @Test fun reportsOmitSecretsRawNoticesPathsAndSuppressedMessages() {
        val cause = IllegalArgumentException("sk-fixture-secret https://user:pass@example.com secret notice")
        val error = IllegalStateException("原始通知内容", cause)
        error.addSuppressed(RuntimeException("suppressed-secret"))
        error.stackTrace = arrayOf(StackTraceElement("com.example.todo.Work", "save", "/private/secret notice.kt", 42))
        val report = CrashReport.render(error, "0.2.0", 37, Instant.EPOCH)
        assertTrue(report.contains("IllegalStateException")); assertTrue(report.contains("IllegalArgumentException"))
        assertTrue(report.contains("com.example.todo.Work.save:42"))
        listOf("sk-fixture-secret", "example.com", "原始通知内容", "secret notice", "suppressed-secret", "/private").forEach { assertFalse(report.contains(it)) }
    }
    @Test fun reportSizeAndCauseCyclesAreBounded() {
        val first = RuntimeException(); val second = RuntimeException()
        first.initCause(second); second.initCause(first)
        first.stackTrace = Array(500) { StackTraceElement("A".repeat(1000), "unsafe method", "file", it) }
        assertTrue(CrashReport.render(first, "0.2.0", 37).length < 24_577)
    }
    @Test fun privateArchiveRetainsOnlyFiveAndCanBeExportedAndCleared() {
        val folder = Files.createTempDirectory("todo-crash-test").toFile()
        try {
            val logs = CrashLogs(folder, "0.2.0", 37)
            assertTrue(logs.exportText().contains("No captured crashes"))
            repeat(7) { logs.record(IllegalStateException("secret-$it")) }
            assertEquals(5, folder.listFiles()!!.size)
            assertFalse(logs.exportText().contains("secret-"))
            logs.clear()
            assertTrue(logs.exportText().contains("No captured crashes"))
        } finally { folder.deleteRecursively() }
    }
}
