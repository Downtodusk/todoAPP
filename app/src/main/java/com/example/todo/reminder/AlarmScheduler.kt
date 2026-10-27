package com.example.todo.reminder

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import com.example.todo.data.local.ReminderEntity
import com.example.todo.MainActivity
import com.example.todo.data.settings.ReminderMode

interface ReminderAlarms {
    suspend fun schedule(reminder: ReminderEntity): Boolean
    fun cancel(id: Long)
}

class AlarmScheduler(private val context: Context,
    private val mode: suspend () -> ReminderMode = { ReminderMode.STANDARD }) : ReminderAlarms {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun permitted() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(context,
        if (id in 1..Int.MAX_VALUE.toLong()) id.toInt() else 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("todo://reminder/$id"))
            .putExtra("reminderId", id).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override suspend fun schedule(reminder: ReminderEntity): Boolean {
        if (reminder.triggerAt <= System.currentTimeMillis() || !permitted()) return false
        return try {
            // URI identity also prevents collisions should the autoincrement ID exceed Int range.
            when (mode()) {
                ReminderMode.STANDARD -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending(reminder.id))
                ReminderMode.ALARM_CLOCK -> {
                    val open = PendingIntent.getActivity(context, 0,
                        Intent(context, MainActivity::class.java).setData(Uri.parse("todo://detail/${reminder.todoId}"))
                            .putExtra("todoId", reminder.todoId).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    manager.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.triggerAt, open), pending(reminder.id))
                }
            }
            true
        } catch (_: SecurityException) { false }
    }
    override fun cancel(id: Long) { manager.cancel(pending(id)); pending(id).cancel() }
}
