package com.example.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DateTimeUtilTest {
    @Test fun `day boundaries are half open`() {
        val date = LocalDate.of(2026, 10, 27)
        val range = DateTimeUtil.dayRange(date, ZoneId.of("Asia/Shanghai"))
        assertEquals(24 * 60 * 60 * 1000L, range.last - range.first + 1)
        assertEquals(date, DateTimeUtil.dateAt(range.first, ZoneId.of("Asia/Shanghai")))
        assertEquals(date, DateTimeUtil.dateAt(range.last, ZoneId.of("Asia/Shanghai")))
        assertEquals(date.plusDays(1), DateTimeUtil.dateAt(range.last + 1, ZoneId.of("Asia/Shanghai")))
    }
    @Test fun `DST short day has 23 hours`() {
        val range = DateTimeUtil.dayRange(LocalDate.of(2026, 3, 8), ZoneId.of("America/Los_Angeles"))
        assertEquals(23 * 3_600_000L, range.last - range.first + 1)
    }
    @Test fun `DST long day has 25 hours`() {
        val range = DateTimeUtil.dayRange(LocalDate.of(2026, 11, 1), ZoneId.of("America/Los_Angeles"))
        assertEquals(25 * 3_600_000L, range.last - range.first + 1)
    }
    @Test fun `instant changes calendar date when timezone changes`() {
        val instant = Instant.parse("2026-01-01T02:00:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2025, 12, 31), DateTimeUtil.dateAt(instant, ZoneId.of("America/Los_Angeles")))
        assertEquals(LocalDate.of(2026, 1, 1), DateTimeUtil.dateAt(instant, ZoneId.of("Asia/Shanghai")))
    }
    @Test fun `week spans month and year`() {
        val week = DateTimeUtil.week(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
        assertEquals(7, week.size)
    }
    @Test fun `leap day remains valid`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2028, 2, 29)
        assertEquals(date, DateTimeUtil.dateAt(DateTimeUtil.startOfDay(date, zone), zone))
    }
}
