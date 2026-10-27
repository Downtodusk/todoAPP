package com.example.todo.reminder

import androidx.room.withTransaction
import com.example.todo.data.local.*
import com.example.todo.domain.DateTimeUtil
import java.time.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RescheduleAll(private val db: TodoDatabase, private val alarms: ReminderAlarms,
    private val notifications: ReminderNotifications, private val clock: Clock = Clock.systemUTC()) {
    private val mutex = Mutex()
    suspend fun run(previousIds: List<Long> = emptyList(), catchUp: Boolean = true, zone: ZoneId = ZoneId.systemDefault()) = mutex.withLock {
        db.withTransaction {
            // The calendar date is authoritative for all-day tasks, including DST boundaries.
            db.todos().allDayItems().forEach { todo ->
                val date = todo.allDayDate?.let(LocalDate::parse) ?: return@forEach
                val due = DateTimeUtil.startOfDay(date, zone)
                if (todo.dueAt != due) {
                    db.todos().update(todo.copy(dueAt = due))
                    db.reminders().forTodo(todo.id).filter { !it.fired }.forEach { reminder ->
                        reminder.allDayMinute?.let { minute ->
                            db.reminders().update(reminder.copy(triggerAt = date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant().toEpochMilli()))
                        }
                    }
                }
            }
            val pending = db.reminders().pending()
            val currentIds = pending.map { it.id }.toSet()
            previousIds.filterNot(currentIds::contains).forEach(alarms::cancel)
            pending.forEach { reminder ->
                if (reminder.triggerAt > clock.millis()) alarms.schedule(reminder)
                else if (catchUp) deliverInTransaction(reminder.id)
            }
        }
    }

    suspend fun deliver(id: Long) = mutex.withLock { db.withTransaction { deliverInTransaction(id) } }

    private suspend fun deliverInTransaction(id: Long) {
        val reminder = db.reminders().find(id) ?: return
        val todo = db.todos().find(reminder.todoId) ?: return
        if (reminder.fired || todo.status != 0 || reminder.triggerAt > clock.millis()) return
        if (notifications.show(todo, reminder)) db.reminders().update(reminder.copy(fired = true))
        // Do not mark denied notifications delivered. A later permission grant can recover them.
    }
}
