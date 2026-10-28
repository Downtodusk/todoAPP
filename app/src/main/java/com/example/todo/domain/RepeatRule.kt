package com.example.todo.domain

import com.example.todo.R
import com.example.todo.i18n.t
import java.time.*

sealed interface RepeatRule {
    data object Daily : RepeatRule
    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule
    data object Monthly : RepeatRule

    companion object {
        fun parse(value: String?): RepeatRule? = when {
            value == null -> null
            value == "DAILY" -> Daily
            value == "MONTHLY" -> Monthly
            value.startsWith("WEEKLY:") -> {
                val numbers = value.substringAfter(':').split(',').map { it.toIntOrNull() }
                require(numbers.isNotEmpty() && numbers.all { it != null && it in 1..7 }) { t(R.string.invalid_repeat_days) }
                Weekly(numbers.map { DayOfWeek.of(requireNotNull(it)) }.toSet())
            }
            else -> throw IllegalArgumentException(t(R.string.invalid_repeat_rule))
        }
    }

    /** Calendar arithmetic preserves wall-clock time through DST. Strictly after both inputs. */
    fun next(due: ZonedDateTime, now: ZonedDateTime, monthDay: Int = due.dayOfMonth): ZonedDateTime {
        require(monthDay in 1..31)
        val threshold = if (now.isAfter(due)) now.withZoneSameInstant(due.zone) else due
        val time = due.toLocalTime()
        fun at(date: LocalDate) = date.atTime(time).atZone(due.zone)
        return when (this) {
            Daily -> at(threshold.toLocalDate()).let { if (it.isAfter(threshold)) it else at(threshold.toLocalDate().plusDays(1)) }
            is Weekly -> {
                require(days.isNotEmpty())
                (0L..7L).asSequence().map { at(threshold.toLocalDate().plusDays(it)) }
                    .first { it.dayOfWeek in days && it.isAfter(threshold) }
            }
            Monthly -> {
                val month = YearMonth.from(threshold)
                fun inMonth(m: YearMonth) = at(m.atDay(minOf(monthDay, m.lengthOfMonth())))
                inMonth(month).let { if (it.isAfter(threshold)) it else inMonth(month.plusMonths(1)) }
            }
        }
    }
}
