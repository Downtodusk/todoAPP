package com.example.todo

import android.content.Context
import com.example.todo.data.local.TodoDatabase
import com.example.todo.data.repo.TodoRepository
import com.example.todo.data.settings.SettingsStore
import com.example.todo.reminder.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reminderIssue = MutableStateFlow<String?>(null)
    val settings = SettingsStore(context)
    val database by lazy { TodoDatabase.create(context) }
    val alarms = AlarmScheduler(context) { settings.settings.first().reminderMode }
    val notifications = NotificationHelper(context)
    val reschedule by lazy { RescheduleAll(database, alarms, notifications) }
    val todos by lazy { TodoRepository(database, onChanged = { oldIds ->
        try { reschedule.run(oldIds, catchUp = false); notifications.prune(database); reminderIssue.value = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { reminderIssue.value = "待办已保存，提醒调度失败，请打开提醒自检" }
    }) }
    fun refreshReminders() { scope.launch {
        try { reschedule.run(); reminderIssue.value = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { reminderIssue.value = "提醒恢复失败，请在自检页重试" }
    } }
}
