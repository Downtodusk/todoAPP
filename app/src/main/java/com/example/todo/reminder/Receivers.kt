package com.example.todo.reminder

import com.example.todo.R
import com.example.todo.i18n.t
import android.content.*
import com.example.todo.App
import kotlinx.coroutines.*

private fun BroadcastReceiver.finishAsync(context: Context, block: suspend (com.example.todo.AppContainer) -> Unit) {
    val result = goAsync()
    val container = (context.applicationContext as App).container
    container.scope.launch {
        try { withTimeout(8_000) { block(container) } }
        catch (_: TimeoutCancellationException) { container.reminderIssue.value = t(R.string.reminder_timeout) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { container.reminderIssue.value = t(R.string.reminder_incomplete) }
        finally { result.finish() }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("reminderId", -1)
        if (id > 0) finishAsync(context) { it.reschedule.deliver(id) }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("todoId", -1)
        val revision = intent.getLongExtra("revision", -1)
        if (id <= 0 || revision < 0) return
        finishAsync(context) { app ->
            val changed = when (intent.action) {
                NotificationHelper.Complete -> app.todos.complete(id, expectedRevision = revision) != null
                NotificationHelper.Snooze -> app.todos.snooze(id, System.currentTimeMillis() + 600_000, revision)
                else -> false
            }
            if (changed) app.notifications.cancel(id)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) {
            finishAsync(context) { it.reschedule.run() }
        }
    }
}
