package com.example.todo.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class NoticeParseTest {
    private val now = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
    private fun local(text: String) = NoticeValidator.validate(LocalNoticeFallback.parse(text, now), now).single()
    @Test fun whitespaceAndLengthAreNormalizedBeforeSending() {
        assertEquals("a b c", prepareNotice(" a\n\t b   c ").text)
        assertTrue(prepareNotice("字".repeat(2001)).truncated)
        assertEquals(2000, prepareNotice("字".repeat(2001)).text.length)
        assertFalse(prepareNotice(" ".repeat(3000) + "字").truncated)
    }
    @Test fun validatorDropsEmptyTitlesAndFlagsInvalidMissingOrUncertainTimes() {
        val items = NoticeValidator.validate(ParseResult(listOf(ParsedTodo("  "), ParsedTodo("检查", "2026-02-30T09:00"),
            ParsedTodo("未来", "2026-10-28T09:00", confidence = .59), ParsedTodo("无时间", confidence = .99))), now)
        assertEquals(3, items.size)
        assertNull(items[0].due); assertTrue(items[0].invalidTime); assertFalse(items[0].reminder)
        assertTrue(items.all { it.uncertain })
        assertTrue(items[1].reminder)
    }
    @Test fun fencedJsonAndUnknownFieldsAreAcceptedButMalformedBodyFails() {
        assertEquals("开会", NoticeValidator.decode("prefix\n```json\n{\"todos\":[{\"title\":\"开会\",\"futureField\":1}]}\n```suffix").todos.single().title)
        assertThrows(AiException::class.java) { NoticeValidator.decode("not JSON") }
        assertThrows(AiException::class.java) { NoticeValidator.decode("{broken") }
    }
    @Test fun pastDatesDisableRemindersAndAllDayIsNormalized() {
        val drafts = NoticeValidator.validate(ParseResult(listOf(ParsedTodo("过去", "2026-10-26T23:59"),
            ParsedTodo("全天", "2026-10-28T15:00", allDay = true))), now)
        assertFalse(drafts[0].reminder); assertTrue(drafts[0].past(now))
        assertEquals(LocalDateTime.parse("2026-10-28T00:00"), drafts[1].due)
    }
    @Test fun keyExamplesResolveRelativeDateRangeAndLocation() {
        assertEquals("2026-10-28T15:00", local("明天下午三点提交报告").due.toString())
        assertEquals("2026-10-30T00:00", local("本周五前提交报告").due.toString())
        val meeting = local("4月12日 14:00-16:00 会议室A 开会")
        assertEquals("2026-04-12T14:00", meeting.due.toString())
        assertEquals("会议室A", meeting.location); assertEquals("结束时间：16:00", meeting.note)
        assertFalse(meeting.reminder)
    }
    @Test fun dateBoundariesAndChineseTimeAreHandled() {
        assertEquals("2026-11-04T09:30", local("下周三上午九点半讨论").due.toString())
        assertEquals("2026-10-29T20:00", local("后天晚上8点讨论").due.toString())
        assertEquals("2026-10-31T00:00", local("月底提交").due.toString())
        assertEquals("2026-10-28T00:00", local("明天凌晨12点值班").due.toString())
        val newYear = LocalNoticeFallback.parse("明天9:00开会", now.withMonth(12).withDayOfMonth(31)).todos.single()
        assertEquals("2027-01-01T09:00", newYear.dueAt)
        assertEquals("2028-02-29T00:00", LocalNoticeFallback.parse("明天提交", now.withYear(2028).withMonth(2).withDayOfMonth(28)).todos.single().dueAt)
    }
    @Test fun invalidDatesAreNotSilentlyConvertedToTodayAndIsoDashIsNotRange() {
        assertNull(local("2月30日 14:00开会").due)
        assertEquals("", local("2026-10-28 09:00开会，15:00复盘").note)
        assertEquals("", local("没有指定时间的工作").note)
        assertNull(local("没有指定时间的工作").due)
    }
    @Test fun localTitleUsesFirstLineAndDoesNotInventMultipleTasks() {
        val parsed = LocalNoticeFallback.parse("第一项工作\n明天第二项", now)
        assertEquals(1, parsed.todos.size); assertEquals("第一项工作", parsed.todos.single().title)
        assertTrue(local("a".repeat(100)).title.length <= 30)
    }
    @Test fun repositorySendsSanitizedInputAndNeverFallsBackAutomatically() = runBlocking {
        var captured = ""
        val repository = NoticeParseRepository({ LlmClient { system, user -> captured = user; assertTrue(system.contains("Asia/Shanghai")); Result.failure(AiException(AiFailure.AUTH)) } }, Clock.fixed(now.toInstant(), now.zone), { now.zone })
        assertTrue(repository.parse("  明天\n交材料  ").isFailure)
        assertEquals("明天 交材料", captured)
        assertEquals(1, repository.local("明天交材料").size)
    }
    @Test fun repositoryCancellationIsNotSwallowed() = runBlocking {
        val repository = NoticeParseRepository({ LlmClient { _, _ -> throw CancellationException() } })
        try { repository.parse("notice"); fail("cancellation required") } catch (_: CancellationException) { }
    }
}
