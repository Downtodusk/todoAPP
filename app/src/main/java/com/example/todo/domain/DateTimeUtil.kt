package com.example.todo.domain

import java.time.*

object DateTimeUtil {
    fun dayRange(date: LocalDate, zone: ZoneId): LongRange =
        date.atStartOfDay(zone).toInstant().toEpochMilli() until
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun dateAt(instant: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun week(date: LocalDate): List<LocalDate> {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return (0L..6L).map(monday::plusDays)
    }
}
