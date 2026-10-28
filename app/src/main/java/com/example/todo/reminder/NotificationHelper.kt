package com.example.todo.reminder

import com.example.todo.R
import com.example.todo.i18n.t
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.todo.MainActivity
import com.example.todo.data.local.*
import java.time.*
import java.time.format.DateTimeFormatter

interface ReminderNotifications {
    fun show(todo: TodoEntity, reminder: ReminderEntity): Boolean
    fun cancel(todoId: Long)
}

class NotificationHelper(private val context: Context) : ReminderNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun createChannel() { manager.createNotificationChannel(NotificationChannel(Channel, t(R.string.task_reminders), NotificationManager.IMPORTANCE_HIGH).apply { description = t(R.string.scheduled_and_snoozed_reminders) }) }
    fun permitted(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun channelEnabled(): Boolean = manager.getNotificationChannel(Channel)?.importance != NotificationManager.IMPORTANCE_NONE
    private fun action(todo: TodoEntity, name: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ActionReceiver::class.java).setAction(name).setData(Uri.parse("todo://action/${todo.id}/${todo.revision}/$name"))
            .putExtra("todoId", todo.id).putExtra("revision", todo.revision).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun show(todo: TodoEntity, reminder: ReminderEntity): Boolean {
        if (!permitted() || !channelEnabled()) return false
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setData(Uri.parse("todo://detail/${todo.id}"))
                .putExtra("todoId", todo.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val time = if (todo.allDay) t(R.string.all_day_date, todo.allDayDate.orEmpty()) else todo.dueAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(t(R.string.mmm_d_hh_mm), com.example.todo.i18n.appLocale()))
        }
        val notification = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(todo.title)
            .setContentText(listOfNotNull(time, todo.location).joinToString(" · "))
            .setContentIntent(open).setAutoCancel(true)
            .setOnlyAlertOnce(manager.activeNotifications.any { it.tag == "todo-${todo.id}" && it.notification.extras.getLong("reminderId", -1) == reminder.id })
            .addExtras(android.os.Bundle().apply { putLong("todoRevision", todo.revision); putLong("reminderId", reminder.id) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, t(R.string.complete), action(todo, Complete))
            .addAction(0, t(R.string.snooze_10_minutes), action(todo, Snooze)).build()
        return try { manager.notify("todo-${todo.id}", 0, notification); true } catch (_: SecurityException) { false }
    }
    override fun cancel(todoId: Long) { manager.cancel("todo-$todoId", 0) }
    suspend fun prune(db: TodoDatabase) {
        manager.activeNotifications.forEach { notification ->
            val id = notification.tag?.removePrefix("todo-")?.toLongOrNull() ?: return@forEach
            val todo = db.todos().find(id)
            if (todo == null || todo.status != 0 || todo.revision != notification.notification.extras.getLong("todoRevision", -1)) cancel(id)
        }
    }
    companion object {
        const val Channel = "todo_reminder"
        const val Complete = "com.example.todo.COMPLETE"
        const val Snooze = "com.example.todo.SNOOZE"
    }
}
