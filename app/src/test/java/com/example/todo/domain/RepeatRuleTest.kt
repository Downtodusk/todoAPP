package com.example.todo.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RepeatRuleTest {
    private fun at(value: String) = ZonedDateTime.parse(value + ":00Z[UTC]")
    @Test fun `daily crosses year`() {
        val due = at("2026-12-31T09:00")
        assertEquals(at("2027-01-01T09:00"), RepeatRule.Daily.next(due, due))
    }
    @Test fun `daily overdue skips to future`() {
        assertEquals(at("2026-10-27T09:00"), RepeatRule.Daily.next(at("2020-01-01T09:00"), at("2026-10-27T08:00")))
    }
    @Test fun `daily exactly now advances`() {
        assertEquals(at("2026-10-28T09:00"), RepeatRule.Daily.next(at("2020-01-01T09:00"), at("2026-10-27T09:00")))
    }
    @Test fun `monthly retains day 31 anchor`() {
        val jan = at("2026-01-31T09:00")
        val feb = RepeatRule.Monthly.next(jan, jan, 31)
        assertEquals(at("2026-02-28T09:00"), feb)
        assertEquals(at("2026-03-31T09:00"), RepeatRule.Monthly.next(feb, feb, 31))
    }
    @Test fun `monthly leap year`() {
        val due = at("2028-01-31T09:00")
        assertEquals(at("2028-02-29T09:00"), RepeatRule.Monthly.next(due, due, 31))
    }
    @Test fun `monthly overdue jumps years`() {
        assertEquals(at("2026-11-30T09:00"), RepeatRule.Monthly.next(at("2020-01-31T09:00"), at("2026-10-31T10:00"), 31))
    }
    @Test fun `weekly supports multiple days`() {
        val rule = RepeatRule.parse("WEEKLY:1,3,5")!!
        assertEquals(at("2026-10-28T09:00"), rule.next(at("2026-10-26T09:00"), at("2026-10-27T10:00")))
    }
    @Test fun `weekly wraps to next week`() {
        val due = at("2026-10-30T09:00")
        assertEquals(at("2026-11-02T09:00"), RepeatRule.parse("WEEKLY:1,5")!!.next(due, due))
    }
    @Test fun `early completion does not repeat same occurrence`() {
        val due = at("2026-10-30T09:00")
        assertEquals(at("2026-10-31T09:00"), RepeatRule.Daily.next(due, due.minusDays(4)))
    }
    @Test fun `DST preserves intended local time`() {
        val due = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, ZoneId.of("America/Los_Angeles"))
        val next = RepeatRule.Daily.next(due, due)
        assertEquals(9, next.hour)
        assertEquals(23, Duration.between(due, next).toHours().toInt())
    }
    @Test fun `null means no repeat`() { assertNull(RepeatRule.parse(null)) }
    @Test(expected = IllegalArgumentException::class) fun `invalid weekday rejected`() { RepeatRule.parse("WEEKLY:0,8") }
    @Test(expected = IllegalArgumentException::class) fun `empty weekly rule rejected`() { RepeatRule.parse("WEEKLY:") }
    @Test(expected = IllegalArgumentException::class) fun `unknown rule rejected`() { RepeatRule.parse("YEARLY") }
}
