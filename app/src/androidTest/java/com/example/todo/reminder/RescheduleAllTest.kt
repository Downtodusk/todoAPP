package com.example.todo.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.todo.data.local.*
import com.example.todo.data.repo.TodoRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class RescheduleAllTest {
    private lateinit var db: TodoDatabase
    private lateinit var repository: TodoRepository
    private lateinit var reschedule: RescheduleAll
    private val now = Instant.parse("2026-10-27T00:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneId.of("UTC"))
    private val alarms = FakeAlarms()
    private val notifications = FakeNotifications()

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TodoDatabase::class.java).build()
        reschedule = RescheduleAll(db, alarms, notifications, clock)
        repository = TodoRepository(db, clock) { reschedule.run(it, catchUp = false, zone = ZoneId.of("UTC")) }
    }
    @After fun close() { db.close() }
    @Test fun repeatedColdStartCatchUpIsIdempotent() = runBlocking {
        val id = repository.save(TodoEntity(title = "过期提醒"), listOf(ReminderEntity(todoId = 0, triggerAt = now - 1)))
        assertTrue(notifications.delivered.isEmpty())
        reschedule.run(); reschedule.run()
        assertEquals(listOf(id), notifications.delivered)
        assertTrue(repository.get(id)!!.reminders.single().fired)
    }
    @Test fun deniedNotificationsCanRecoverAfterPermissionGrant() = runBlocking {
        notifications.allowed = false
        val id = repository.save(TodoEntity(title = "拒绝通知"), listOf(ReminderEntity(todoId = 0, triggerAt = now - 1)))
        reschedule.run()
        assertFalse(repository.get(id)!!.reminders.single().fired)
        notifications.allowed = true
        reschedule.run(); reschedule.run()
        assertEquals(listOf(id), notifications.delivered)
    }
    @Test fun futureSchedulingUsesStableReminderIdentity() = runBlocking {
        val id = repository.save(TodoEntity(title = "未来"), listOf(ReminderEntity(todoId = 0, triggerAt = now + 60_000)))
        val reminder = repository.get(id)!!.reminders.single()
        reschedule.run(); reschedule.run()
        assertEquals(mapOf(reminder.id to reminder), alarms.scheduled)
        assertTrue(notifications.delivered.isEmpty())
    }
    @Test fun deletingCancelsAlarmAndUndoReRegistersIt() = runBlocking {
        val id = repository.save(TodoEntity(title = "删除撤销"), listOf(ReminderEntity(todoId = 0, triggerAt = now + 60_000)))
        val reminder = repository.get(id)!!.reminders.single()
        val undo = repository.delete(id)!!
        assertFalse(alarms.scheduled.containsKey(reminder.id))
        reschedule.deliver(reminder.id)
        assertTrue(notifications.delivered.isEmpty())
        assertTrue(repository.undo(undo))
        assertEquals(reminder, alarms.scheduled[reminder.id])
    }
    @Test fun completedTodoNeverDelivers() = runBlocking {
        val id = repository.save(TodoEntity(title = "已完成"), listOf(ReminderEntity(todoId = 0, triggerAt = now - 1)))
        val reminder = repository.get(id)!!.reminders.single()
        repository.complete(id)
        reschedule.deliver(reminder.id); reschedule.run()
        assertTrue(notifications.delivered.isEmpty())
    }
    @Test fun staleActionCannotCompleteNextRecurringOccurrence() = runBlocking {
        val id = repository.save(TodoEntity(title = "每天", dueAt = now - 60_000, repeatRule = "DAILY"))
        val old = repository.get(id)!!.todo
        assertNotNull(repository.complete(id, expectedRevision = old.revision))
        assertNull(repository.complete(id, expectedRevision = old.revision))
        assertFalse(repository.snooze(id, now + 600_000, old.revision))
        assertEquals(0, repository.get(id)!!.todo.status)
    }
    @Test fun repeatedSnoozeActionCreatesOnlyOneReminder() = runBlocking {
        val id = repository.save(TodoEntity(title = "稍后"))
        val revision = repository.get(id)!!.todo.revision
        assertTrue(repository.snooze(id, now + 600_000, revision))
        assertFalse(repository.snooze(id, now + 600_000, revision))
        assertEquals(1, repository.get(id)!!.reminders.size)
    }
    @Test fun allDayRebasesButTimedInstantStaysFixed() = runBlocking {
        val date = LocalDate.of(2026, 10, 28)
        val utc = ZoneId.of("UTC")
        val shanghai = ZoneId.of("Asia/Shanghai")
        val due = date.atStartOfDay(utc).toInstant().toEpochMilli()
        val id = repository.save(TodoEntity(title = "全天", dueAt = due, allDay = true, allDayDate = date.toString()),
            listOf(ReminderEntity(todoId = 0, triggerAt = due + 9 * 3_600_000, allDayMinute = 540)), utc)
        val timed = repository.save(TodoEntity(title = "定时", dueAt = due), listOf(ReminderEntity(todoId = 0, triggerAt = due)), utc)
        reschedule.run(zone = shanghai)
        assertEquals(date.toString(), repository.get(id)!!.todo.allDayDate)
        assertEquals(date.atStartOfDay(shanghai).toInstant().toEpochMilli(), repository.get(id)!!.todo.dueAt)
        assertEquals(date.atTime(9, 0).atZone(shanghai).toInstant().toEpochMilli(), repository.get(id)!!.reminders.single().triggerAt)
        assertEquals(due, repository.get(timed)!!.todo.dueAt)
        assertEquals(due, repository.get(timed)!!.reminders.single().triggerAt)
    }
    @Test fun earlyOrDuplicateAlarmDeliveryIsIgnored() = runBlocking {
        val id = repository.save(TodoEntity(title = "未来"), listOf(ReminderEntity(todoId = 0, triggerAt = now + 1)))
        reschedule.deliver(repository.get(id)!!.reminders.single().id)
        assertTrue(notifications.delivered.isEmpty())
        val past = repository.save(TodoEntity(title = "过去"), listOf(ReminderEntity(todoId = 0, triggerAt = now - 1)))
        val reminder = repository.get(past)!!.reminders.single()
        reschedule.deliver(reminder.id); reschedule.deliver(reminder.id)
        assertEquals(listOf(past), notifications.delivered)
    }

    private class FakeAlarms : ReminderAlarms {
        val scheduled = mutableMapOf<Long, ReminderEntity>()
        override fun schedule(reminder: ReminderEntity): Boolean { scheduled[reminder.id] = reminder; return true }
        override fun cancel(id: Long) { scheduled.remove(id) }
    }
    private class FakeNotifications : ReminderNotifications {
        var allowed = true
        val delivered = mutableListOf<Long>()
        override fun show(todo: TodoEntity, reminder: ReminderEntity): Boolean {
            if (!allowed) return false
            delivered.add(todo.id); return true
        }
        override fun cancel(todoId: Long) = Unit
    }
}
