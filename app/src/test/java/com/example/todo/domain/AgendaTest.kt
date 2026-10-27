package com.example.todo.domain

import com.example.todo.data.local.TodoEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AgendaTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 27)
    private fun day(id: Long, date: LocalDate) = TodoEntity(id, "全天", dueAt = DateTimeUtil.startOfDay(date, zone), allDay = true, allDayDate = date.toString())
    @Test fun `partition includes undated and completion date not due date`() {
        val yesterday = day(1, today.minusDays(1))
        val allDay = day(2, today)
        val timed = TodoEntity(3, "定时", dueAt = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        val undated = TodoEntity(4, "无时间")
        val done = yesterday.copy(id = 5, status = 1, completedAt = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
        val agenda = partitionAgenda(listOf(timed, undated, done, yesterday, allDay), today, today, zone)
        assertEquals(listOf(1L), agenda.overdue.map { it.id })
        assertEquals(listOf(2L, 3L), agenda.scheduled.map { it.id })
        assertEquals(listOf(4L), agenda.undated.map { it.id })
        assertEquals(listOf(5L), agenda.completed.map { it.id })
    }
    @Test fun `future view does not invent overdue items`() {
        val agenda = partitionAgenda(listOf(day(1, today), day(2, today.plusDays(1))), today.plusDays(1), today, zone)
        assertTrue(agenda.overdue.isEmpty())
        assertEquals(listOf(2L), agenda.scheduled.map { it.id })
    }
    @Test fun `all day date survives timezone shift`() {
        val todo = day(1, today)
        val other = ZoneId.of("America/Los_Angeles")
        assertEquals(today, todo.calendarDate(other))
        assertEquals(listOf(todo), partitionAgenda(listOf(todo), today, today, other).scheduled)
    }
    @Test fun `past selected day has no duplicate keys across sections`() {
        val item = day(1, today.minusDays(1))
        val agenda = partitionAgenda(listOf(item), today.minusDays(1), today, zone)
        assertTrue(agenda.overdue.isEmpty())
        assertEquals(listOf(item), agenda.scheduled)
    }
}
