package com.example.todo.data.repo

import com.example.todo.R
import com.example.todo.i18n.t
import androidx.room.withTransaction
import com.example.todo.data.local.*
import com.example.todo.domain.*
import java.time.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TodoSnapshot(val todo: TodoEntity, val reminders: List<ReminderEntity>)
data class UndoChange(val before: TodoSnapshot, val expectedRevision: Long?)

/** A single transaction owns each change to a todo and all of its reminder rows. */
class TodoRepository(val database: TodoDatabase, private val clock: Clock = Clock.systemUTC(),
    private val onChanged: suspend (List<Long>) -> Unit = {}) {
    private val todos = database.todos()
    private val reminders = database.reminders()
    val all: Flow<List<TodoEntity>> = todos.observeAll()
    private suspend fun <T> change(block: suspend () -> T): T {
        val (oldIds, result) = database.withTransaction { reminders.pending().map { it.id } to block() }
        // Schedule only after the commit. Permission failures must not roll back a saved todo.
        onChanged(oldIds)
        return result
    }
    fun agenda(selected: LocalDate, today: LocalDate, zone: ZoneId): Flow<Agenda> =
        all.map { partitionAgenda(it, selected, today, zone) }

    suspend fun get(id: Long): TodoSnapshot? = database.withTransaction {
        todos.find(id)?.let { TodoSnapshot(it, reminders.forTodo(id)) }
    }

    suspend fun save(input: TodoEntity, newReminders: List<ReminderEntity>? = null, zone: ZoneId = ZoneId.systemDefault()): Long =
        change { saveInTransaction(input, newReminders, zone) }

    suspend fun importBatch(items: List<Pair<TodoEntity, List<ReminderEntity>>>, zone: ZoneId = ZoneId.systemDefault()): List<Long> = change {
        require(items.isNotEmpty()) { t(R.string.select_at_least_one_task) }
        items.map { (todo, reminders) ->
            require(todo.id == 0L && todo.status == 0)
            saveInTransaction(todo, reminders, zone)
        }
    }

    private suspend fun saveInTransaction(input: TodoEntity, newReminders: List<ReminderEntity>?, zone: ZoneId): Long {
        val title = input.title.trim()
        require(title.length in 1..200) { t(R.string.the_title_must_contain_1_200_characters) }
        require(input.status in 0..1)
        val rule = RepeatRule.parse(input.repeatRule)
        require(rule == null || input.dueAt != null) { t(R.string.set_a_date_before_repeating_a_task) }
        require(!input.allDay || (input.dueAt != null && input.allDayDate != null))
        val previous = if (input.id != 0L) requireNotNull(todos.find(input.id)) { t(R.string.task_deleted) } else null
        require(previous == null || previous.revision == input.revision) { t(R.string.this_task_changed_reopen_it_before_editing) }
        val now = clock.millis()
        val dueAt = if (input.allDay) DateTimeUtil.startOfDay(LocalDate.parse(input.allDayDate), zone) else input.dueAt
        val dueChanged = previous?.dueAt != dueAt || previous?.allDayDate != input.allDayDate
        val calendarChanged = previous?.calendarDate(zone) != if (input.allDay) LocalDate.parse(input.allDayDate) else dueAt?.let { DateTimeUtil.dateAt(it, zone) }
        val value = input.copy(title = title, dueAt = dueAt, allDayDate = if (input.allDay) input.allDayDate else null,
            repeatAnchorDay = if (rule == RepeatRule.Monthly) {
                if (calendarChanged || previous?.repeatRule != "MONTHLY") dueAt?.let { DateTimeUtil.dateAt(it, zone).dayOfMonth }
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
                val trigger = if (value.allDay && it.allDayMinute != null) LocalDate.parse(value.allDayDate)
                    .atTime(LocalTime.of(it.allDayMinute / 60, it.allDayMinute % 60)).atZone(zone).toInstant().toEpochMilli()
                    else dueAt + (it.triggerAt - previous.dueAt)
                reminders.insert(it.copy(id = 0, triggerAt = trigger, fired = false))
            }
        }
        return id
    }

    suspend fun complete(id: Long, zone: ZoneId = ZoneId.systemDefault(), expectedRevision: Long? = null): UndoChange? = change {
        val old = get(id) ?: return@change null
        if (old.todo.status != 0 || (expectedRevision != null && old.todo.revision != expectedRevision)) return@change null
        val now = clock.millis()
        val rule = RepeatRule.parse(old.todo.repeatRule)
        val next = if (rule != null && old.todo.dueAt != null) {
            val due = if (old.todo.allDay && old.todo.allDayDate != null) LocalDate.parse(old.todo.allDayDate).atStartOfDay(zone)
                else Instant.ofEpochMilli(old.todo.dueAt).atZone(zone)
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
            val trigger = if (it.allDayMinute != null) next.toLocalDate().atTime(LocalTime.of(it.allDayMinute / 60, it.allDayMinute % 60)).atZone(zone).toInstant().toEpochMilli()
                else requireNotNull(updated.dueAt) + (it.triggerAt - requireNotNull(old.todo.dueAt))
            reminders.insert(it.copy(id = 0, triggerAt = trigger, fired = false))
        }
        UndoChange(old, updated.revision)
    }

    suspend fun delete(id: Long): UndoChange? = change {
        val old = get(id) ?: return@change null
        todos.delete(id)
        UndoChange(old, null)
    }

    suspend fun undo(change: UndoChange): Boolean = change {
        val current = todos.find(change.before.todo.id)
        if (change.expectedRevision == null) {
            if (current != null) return@change false
            todos.insert(change.before.todo.copy(revision = change.before.todo.revision + 2, updatedAt = clock.millis()))
        } else {
            if (current?.revision != change.expectedRevision) return@change false
            todos.update(change.before.todo.copy(revision = current.revision + 1, updatedAt = clock.millis()))
        }
        reminders.deleteForTodo(change.before.todo.id)
        change.before.reminders.forEach { reminders.insert(it) }
        true
    }

    suspend fun reopen(id: Long) = change {
        val item = todos.find(id) ?: return@change
        todos.update(item.copy(status = 0, completedAt = null, updatedAt = clock.millis(), revision = item.revision + 1))
    }

    suspend fun copy(id: Long): Long? = get(id)?.let {
        save(it.todo.copy(id = 0, status = 0, completedAt = null, revision = 0),
            it.reminders.map { reminder -> reminder.copy(id = 0, fired = false) })
    }

    suspend fun snooze(id: Long, triggerAt: Long, expectedRevision: Long? = null): Boolean = change {
        val todo = todos.find(id) ?: return@change false
        if (todo.status != 0 || (expectedRevision != null && todo.revision != expectedRevision)) return@change false
        require(triggerAt > clock.millis())
        reminders.forTodo(id).filter { !it.fired && it.triggerAt <= clock.millis() }.forEach { reminders.update(it.copy(fired = true)) }
        reminders.insert(ReminderEntity(todoId = id, triggerAt = triggerAt))
        // Invalidate an already-delivered notification action, making repeated taps idempotent.
        todos.update(todo.copy(revision = todo.revision + 1, updatedAt = clock.millis()))
        true
    }
}
