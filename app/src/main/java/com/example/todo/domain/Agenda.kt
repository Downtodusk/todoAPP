package com.example.todo.domain

import com.example.todo.data.local.TodoEntity
import java.time.*

data class Agenda(
    val overdue: List<TodoEntity>,
    val scheduled: List<TodoEntity>,
    val undated: List<TodoEntity>,
    val completed: List<TodoEntity>,
) {
    val empty get() = overdue.isEmpty() && scheduled.isEmpty() && undated.isEmpty() && completed.isEmpty()
}

fun TodoEntity.calendarDate(zone: ZoneId): LocalDate? =
    if (allDay && allDayDate != null) LocalDate.parse(allDayDate) else dueAt?.let { DateTimeUtil.dateAt(it, zone) }

fun partitionAgenda(todos: List<TodoEntity>, selected: LocalDate, today: LocalDate, zone: ZoneId): Agenda {
    val pending = todos.filter { it.status == 0 }
    return Agenda(
        overdue = if (selected == today) pending.filter { it.calendarDate(zone)?.isBefore(today) == true }
            .sortedBy { it.dueAt } else emptyList(),
        scheduled = pending.filter { it.calendarDate(zone) == selected }
            .sortedWith(compareByDescending<TodoEntity> { it.allDay }.thenBy { it.dueAt }.thenBy { it.id }),
        undated = pending.filter { it.dueAt == null }.sortedByDescending { it.createdAt },
        completed = todos.filter { it.status == 1 && it.completedAt?.let { time -> DateTimeUtil.dateAt(time, zone) == selected } == true }
            .sortedByDescending { it.completedAt },
    )
}
