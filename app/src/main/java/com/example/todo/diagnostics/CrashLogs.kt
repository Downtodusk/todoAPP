package com.example.todo.diagnostics

import java.io.File
import java.time.Instant

/** A bounded app-private archive, exported only through a user-selected document destination. */
class CrashLogs(private val directory: File, private val version: String, private val sdk: Int) {
    private var installed = false
    @Synchronized fun install() {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { record(error) } catch (_: Throwable) { /* A diagnostic failure must not replace the crash. */ }
            finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
    }
    @Synchronized fun record(error: Throwable) {
        check(directory.mkdirs() || directory.isDirectory)
        val file = File.createTempFile("crash-", ".txt", directory)
        file.writeText(CrashReport.render(error, version, sdk))
        files().filter { it != file }.drop(4).forEach { it.delete() }
    }
    @Synchronized fun exportText(): String = buildString {
        appendLine("Todo diagnostics export · ${Instant.now()}")
        appendLine("App $version · Android API $sdk")
        appendLine("No todo content, notice text, service URLs or API keys are included.")
        val entries = files().take(5)
        if (entries.isEmpty()) appendLine("No captured crashes.")
        entries.forEach { appendLine(); append(it.inputStream().bufferedReader().use { reader ->
            val buffer = CharArray(24_576)
            val count = reader.read(buffer)
            if (count > 0) String(buffer, 0, count) else ""
        }) }
    }
    @Synchronized fun clear() { files().forEach { check(it.delete()) } }
    private fun files() = directory.listFiles()?.filter { it.isFile && it.name.startsWith("crash-") && it.extension == "txt" }
        ?.sortedByDescending { it.lastModified() }.orEmpty()
}
