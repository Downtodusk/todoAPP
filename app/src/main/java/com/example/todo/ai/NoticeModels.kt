package com.example.todo.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*

@Serializable
data class ParsedTodo(val title: String, val dueAt: String? = null, val allDay: Boolean = false,
    val location: String? = null, val note: String? = null, val confidence: Double? = null)
@Serializable data class ParseResult(val todos: List<ParsedTodo> = emptyList())

data class NoticeInput(val text: String, val truncated: Boolean)
fun prepareNotice(raw: String): NoticeInput {
    val normalized = raw.trim().replace(Regex("\\s+"), " ")
    return NoticeInput(normalized.take(2000), normalized.length > 2000)
}

data class TodoDraft(val id: Int, val selected: Boolean = true, val title: String,
    val due: LocalDateTime? = null, val allDay: Boolean = false, val location: String = "", val note: String = "",
    val reminder: Boolean = false, val uncertain: Boolean = false, val invalidTime: Boolean = false) {
    fun past(now: ZonedDateTime) = due?.let { if (allDay) it.toLocalDate() < now.toLocalDate() else it.atZone(now.zone).toInstant() < now.toInstant() } ?: false
}

object NoticeValidator {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    fun decode(raw: String): ParseResult {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start < 0 || end < start) throw AiException(AiFailure.RESPONSE)
        return try { json.decodeFromString<ParseResult>(raw.substring(start, end + 1)) }
        catch (_: Exception) { throw AiException(AiFailure.RESPONSE) }
    }
    fun validate(result: ParseResult, now: ZonedDateTime): List<TodoDraft> = result.todos.mapIndexedNotNull { index, item ->
        val title = item.title.trim().take(30)
        if (title.isEmpty()) return@mapIndexedNotNull null
        val due = item.dueAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            ?.let { if (item.allDay) it.toLocalDate().atStartOfDay() else it }
        val draft = TodoDraft(index, title = title, due = due, allDay = item.allDay && due != null,
            location = item.location.orEmpty().trim(), note = item.note.orEmpty().trim(),
            uncertain = due == null || item.confidence?.let { it < .6 } == true,
            invalidTime = item.dueAt != null && due == null)
        draft.copy(reminder = due != null && !draft.past(now))
    }
}
