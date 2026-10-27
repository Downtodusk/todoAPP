package com.example.todo.ai

import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

class NoticeParseRepository(private val client: suspend () -> LlmClient,
    private val clock: Clock = Clock.systemUTC(), private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {
    suspend fun parse(input: String): Result<List<TodoDraft>> {
        val prepared = prepareNotice(input)
        if (prepared.text.isBlank()) return Result.success(emptyList())
        return try {
            val now = ZonedDateTime.now(clock.withZone(zone()))
            client().complete(PromptBuilder.system(now), prepared.text).mapCatching { NoticeValidator.validate(NoticeValidator.decode(it), now) }
        } catch (e: CancellationException) { throw e }
        catch (e: AiException) { Result.failure(e) }
        catch (_: Exception) { Result.failure(AiException(AiFailure.CONFIG)) }
    }
    fun local(input: String): List<TodoDraft> {
        val now = ZonedDateTime.now(clock.withZone(zone()))
        return NoticeValidator.validate(LocalNoticeFallback.parse(input, now), now)
    }
}
