package com.example.todo.data.repo

import androidx.room.withTransaction
import com.example.todo.data.local.*
import com.example.todo.domain.*
import java.time.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TodoSnapshot(val todo: TodoEntity, val reminders: List<ReminderEntity>)
data class UndoChange(val before: TodoSnapshot, val expectedRevision: Long?)

/** A single transaction owns each change to a todo and all of its reminder rows. */
class TodoRepository(val database: TodoDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val todos = database.todos()
    private val reminders = database.reminders()
    val all: Flow<List<TodoEntity>> = todos.observeAll()
    fun agenda(selected: LocalDate, today: LocalDate, zone: ZoneId): Flow<Agenda> =
        all.map { partitionAgenda(it, selected, today, zone) }

    suspend fun get(id: Long): TodoSnapshot? = database.withTransaction {
        todos.find(id)?.let { TodoSnapshot(it, reminders.forTodo(id)) }
    }

    suspend fun save(input: TodoEntity, newReminders: List<ReminderEntity>? = null, zone: ZoneId = ZoneId.systemDefault()): Long = database.withTransaction {
        val title = input.title.trim()
        require(title.length in 1..200) { "标题需要 1–200 个字符" }
        require(input.status in 0..1)
        val rule = RepeatRule.parse(input.repeatRule)
        require(rule == null || input.dueAt != null) { "请先设置重复事项的日期" }
        require(!input.allDay || (input.dueAt != null && input.allDayDate != null))
        val previous = if (input.id != 0L) requireNotNull(todos.find(input.id)) { "待办已删除" } else null
        require(previous == null || previous.revision == input.revision) { "待办已发生变化，请重新打开后编辑" }
        val now = clock.millis()
        val dueAt = if (input.allDay) DateTimeUtil.startOfDay(LocalDate.parse(input.allDayDate), zone) else input.dueAt
        val dueChanged = previous?.dueAt != dueAt || previous?.allDayDate != input.allDayDate
        val value = input.copy(title = title, dueAt = dueAt, allDayDate = if (input.allDay) input.allDayDate else null,
            repeatAnchorDay = if (rule == RepeatRule.Monthly) {
                if (dueChanged || previous?.repeatRule != "MONTHLY") dueAt?.let { DateTimeUtil.dateAt(it, zone).dayOfMonth }
                else previous.repeatAnchorDay
            } else null,
            createdAt = previous?.createdAt ?: now, updatedAt = now, revision = (previous?.revision ?: 0) + 1)
        val id = if (previous == null) todos.insert(value) else { todos.update(value); value.id }
        if (newReminders != null) {
            reminders.deleteForTodo(id)
            newReminders.forEach { reminders.insert(it.copy(id = 0, todoId = id)) }
        } else if (previous != null && dueChanged) {
            val old = reminders.forTodo(id)
            reminders.deleteForTodo(id)
            if (dueAt != null && previous.dueAt != null) old.forEach {
                reminders.insert(it.copy(id = 0, triggerAt = dueAt + (it.triggerAt - previous.dueAt), fired = false))
            }
        }
        id
    }

    suspend fun complete(id: Long, zone: ZoneId = ZoneId.systemDefault()): UndoChange? = database.withTransaction {
        val old = get(id) ?: return@withTransaction null
        if (old.todo.status != 0) return@withTransaction null
        val now = clock.millis()
        val rule = RepeatRule.parse(old.todo.repeatRule)
        val next = if (rule != null && old.todo.dueAt != null) {
            val due = Instant.ofEpochMilli(old.todo.dueAt).atZone(zone)
            rule.next(due, Instant.ofEpochMilli(now).atZone(zone), old.todo.repeatAnchorDay ?: due.dayOfMonth)
        } else null
        val updated = old.todo.copy(
            status = if (next == null) 1 else 0, completedAt = if (next == null) now else null,
            dueAt = next?.toInstant()?.toEpochMilli() ?: old.todo.dueAt,
            allDayDate = if (next != null && old.todo.allDay) next.toLocalDate().toString() else old.todo.allDayDate,
            updatedAt = now, revision = old.todo.revision + 1)
        todos.update(updated)
        reminders.deleteForTodo(id)
        if (next != null) old.reminders.forEach {
            val trigger = if (it.allDayMinute != null) next.toLocalDate().atStartOfDay(zone).plusMinutes(it.allDayMinute.toLong()).toInstant().toEpochMilli()
                else requireNotNull(updated.dueAt) + (it.triggerAt - requireNotNull(old.todo.dueAt))
            reminders.insert(it.copy(id = 0, triggerAt = trigger, fired = false))
        }
        UndoChange(old, updated.revision)
    }

    suspend fun delete(id: Long): UndoChange? = database.withTransaction {
        val old = get(id) ?: return@withTransaction null
        todos.delete(id)
        UndoChange(old, null)
    }

    suspend fun undo(change: UndoChange): Boolean = database.withTransaction {
        val current = todos.find(change.before.todo.id)
        if (change.expectedRevision == null) {
            if (current != null) return@withTransaction false
            todos.insert(change.before.todo.copy(revision = change.before.todo.revision + 2, updatedAt = clock.millis()))
        } else {
            if (current?.revision != change.expectedRevision) return@withTransaction false
            todos.update(change.before.todo.copy(revision = current.revision + 1, updatedAt = clock.millis()))
        }
        reminders.deleteForTodo(change.before.todo.id)
        change.before.reminders.forEach { reminders.insert(it) }
        true
    }

    suspend fun reopen(id: Long) = database.withTransaction {
        val item = todos.find(id) ?: return@withTransaction
        todos.update(item.copy(status = 0, completedAt = null, updatedAt = clock.millis(), revision = item.revision + 1))
    }

    suspend fun copy(id: Long): Long? = get(id)?.let {
        save(it.todo.copy(id = 0, status = 0, completedAt = null, revision = 0),
            it.reminders.map { reminder -> reminder.copy(id = 0, fired = false) })
    }
}
