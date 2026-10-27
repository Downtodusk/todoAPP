package com.example.todo.reminder

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import com.example.todo.data.local.ReminderEntity

interface ReminderAlarms {
    fun schedule(reminder: ReminderEntity): Boolean
    fun cancel(id: Long)
}

class AlarmScheduler(private val context: Context) : ReminderAlarms {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun permitted() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(context,
        if (id in 1..Int.MAX_VALUE.toLong()) id.toInt() else 0,
        Intent(context, ReminderReceiver::class.java).setData(Uri.parse("todo://reminder/$id"))
            .putExtra("reminderId", id).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    override fun schedule(reminder: ReminderEntity): Boolean {
        if (reminder.triggerAt <= System.currentTimeMillis() || !permitted()) return false
        return try {
            // URI identity also prevents collisions should the autoincrement ID exceed Int range.
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending(reminder.id))
            true
        } catch (_: SecurityException) { false }
    }
    override fun cancel(id: Long) { manager.cancel(pending(id)); pending(id).cancel() }
}
