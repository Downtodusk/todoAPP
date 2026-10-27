package com.example.todo.ai

import java.time.*
import java.time.temporal.TemporalAdjusters

object LocalNoticeFallback {
    private val explicitDate = Regex("(?:(\\d{4})年)?(\\d{1,2})[月/](\\d{1,2})日?")
    private val isoDate = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
    private val weekday = Regex("(下星期|下周|本周|这周|星期|周)([一二三四五六日天])")
    private val time = Regex("(?:(上午|下午|晚上|中午|凌晨|早上)\\s*)?([零〇一二三四五六七八九十两\\d]{1,3})\\s*(?:[:：]([0-5]?\\d)|点(?:(半)|([0-5]?\\d)分?)?)(?!\\d)")
    private fun number(text: String): Int? = text.toIntOrNull() ?: run {
        val digits = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3,
            '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
        if (text.length == 1 && text != "十") digits[text[0]]
        else if ('十' in text) {
            val parts = text.split('十')
            if (parts.size != 2) null else (parts[0].firstOrNull()?.let(digits::get) ?: 1) * 10 + (parts[1].firstOrNull()?.let(digits::get) ?: 0)
        } else null
    }
    fun parse(raw: String, now: ZonedDateTime): ParseResult {
        if (raw.isBlank()) return ParseResult()
        val text = raw.take(2000)
        val today = now.toLocalDate()
        val hasExplicitDate = isoDate.containsMatchIn(text) || explicitDate.containsMatchIn(text)
        var date = isoDate.find(text)?.let { match -> runCatching { LocalDate.parse(match.value) }.getOrNull() }
            ?: explicitDate.find(text)?.let { match -> runCatching {
                LocalDate.of(match.groupValues[1].toIntOrNull() ?: today.year, match.groupValues[2].toInt(), match.groupValues[3].toInt())
            }.getOrNull() }
        if (date == null && !hasExplicitDate) date = when {
            "后天" in text -> today.plusDays(2)
            "明天" in text -> today.plusDays(1)
            "今天" in text -> today
            "月底" in text -> today.with(TemporalAdjusters.lastDayOfMonth())
            else -> weekday.find(text)?.let { match ->
                val day = "一二三四五六日".indexOf(match.groupValues[2].replace('天', '日')) + 1
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                when (match.groupValues[1]) {
                    "下周", "下星期" -> monday.plusWeeks(1).plusDays(day - 1L)
                    "本周", "这周" -> monday.plusDays(day - 1L)
                    else -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(day)))
                }
            }
        }
        val matches = time.findAll(text).toList()
        val times = matches.mapNotNull { match ->
            var hour = number(match.groupValues[2]) ?: return@mapNotNull null
            if (match.groupValues[1] in listOf("下午", "晚上", "中午") && hour in 1..11) hour += 12
            if (match.groupValues[1] == "凌晨" && hour == 12) hour = 0
            val minute = if (match.groupValues[4] == "半") 30 else (match.groupValues[3].ifEmpty { match.groupValues[5] }).toIntOrNull() ?: 0
            runCatching { LocalTime.of(hour, minute) }.getOrNull()
        }.toList()
        if (date == null && !hasExplicitDate && times.isNotEmpty()) date = today
        val location = Regex("地点[:：]\\s*([^，。；;\\n]+)").find(text)?.groupValues?.get(1)?.trim()
            ?: Regex("会议室\\s*[A-Za-z0-9一二三四五六七八九十]+").find(text)?.value
        val note = if (times.size > 1 && matches.size > 1 && Regex("\\s*[-–至到]\\s*").matches(text.substring(matches[0].range.last + 1, matches[1].range.first))) "结束时间：${times[1]}" else null
        return ParseResult(listOf(ParsedTodo(title = text.lineSequence().first().trim().take(30),
            dueAt = date?.atTime(times.firstOrNull() ?: LocalTime.MIDNIGHT)?.toString(),
            allDay = date != null && times.isEmpty(), location = location, note = note, confidence = .5)))
    }
}
