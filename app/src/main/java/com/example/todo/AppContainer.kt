package com.example.todo

import com.example.todo.R
import com.example.todo.i18n.t
import android.content.Context
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.repo.TodoRepository
import com.example.todo.data.settings.SettingsStore
import com.example.todo.data.settings.SecretStore
import com.example.todo.ai.*
import com.example.todo.reminder.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val crashLogs = com.example.todo.diagnostics.CrashLogs(java.io.File(context.filesDir, "diagnostics"),
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown", android.os.Build.VERSION.SDK_INT)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reminderIssue = MutableStateFlow<String?>(null)
    val settings = SettingsStore(context)
    val secrets = SecretStore()
    val noticeParser = NoticeParseRepository(client = {
        val config = settings.ai.first()
        if (!config.consentGranted) throw AiException(AiFailure.CONSENT)
        if (!config.configured) throw AiException(AiFailure.CONFIG)
        llmClient(config, settings.aiKey(config, secrets))
    })
    val database by lazy { TodoDatabase.create(context) }
    val alarms = AlarmScheduler(context) { settings.settings.first().reminderMode }
    val notifications = NotificationHelper(context)
    val reschedule by lazy { RescheduleAll(database, alarms, notifications) }
    val todos by lazy { TodoRepository(database, onChanged = { oldIds ->
        try { reschedule.run(oldIds, catchUp = false); notifications.prune(database); reminderIssue.value = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { reminderIssue.value = t(R.string.task_saved_but_reminder_scheduling_failed_open_reminder_check) }
    }) }
    fun refreshReminders() { scope.launch {
        try { reschedule.run(); reminderIssue.value = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { reminderIssue.value = t(R.string.could_not_restore_reminders_try_again_in_reminder_check) }
    } }
}
