package com.example.todo.ui

import com.example.todo.ui.add.monthCells
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class MonthCalendarTest {
    @Test fun mondayFirstGridIncludesEveryDayExactlyOnce() {
        for (year in listOf(2026, 2028)) for (month in 1..12) {
            val value = YearMonth.of(year, month)
            val cells = monthCells(value)
            assertEquals(DayOfWeek.MONDAY, cells.first().dayOfWeek)
            assertEquals(0, cells.size % 7)
            assertEquals(value.lengthOfMonth(), cells.count { YearMonth.from(it) == value })
            assertEquals(cells.size, cells.distinct().size)
            assertTrue(cells.zipWithNext().all { (a, b) -> b == a.plusDays(1) })
        }
    }
    @Test fun gridCrossesYearAndIncludesSixWeekMonthsAndLeapDay() {
        assertEquals(LocalDate.of(2025, 12, 29), monthCells(YearMonth.of(2026, 1)).first())
        assertEquals(42, monthCells(YearMonth.of(2026, 8)).size)
        assertTrue(LocalDate.of(2028, 2, 29) in monthCells(YearMonth.of(2028, 2)))
    }
}
