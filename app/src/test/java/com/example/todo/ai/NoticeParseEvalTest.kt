package com.example.todo.ai

import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

/** Synthetic cases only; no production messages or secrets belong in these resources. */
class NoticeParseEvalTest {
    private fun resource(name: String) = requireNotNull(javaClass.classLoader!!.getResourceAsStream("notices/$name")).bufferedReader().use { it.readText() }
    private val ids get() = resource("index.txt").lineSequence().filter { it.isNotBlank() }.toList()
    @Test fun fixturePairsHaveValidSchemaAndCoverRequiredExamples() {
        assertTrue(ids.size >= 30)
        ids.forEach { id ->
            assertTrue(resource("$id.txt").isNotBlank())
            val result = NoticeValidator.decode(resource("$id.expected.json"))
            result.todos.forEach { item -> assertTrue(item.title.isNotBlank()); item.dueAt?.let(LocalDateTime::parse) }
        }
    }
    @Ignore("Explicit paid-network opt-in only: use evaluateNotices with LLM_BASE_URL, LLM_KEY, LLM_MODEL")
    @Test fun evaluateConfiguredModel() = evaluate()

    fun evaluate() = runBlocking {
        val config = AiConfiguration(baseUrl = requireNotNull(System.getenv("LLM_BASE_URL")), model = requireNotNull(System.getenv("LLM_MODEL"))).validated()
        val key = requireNotNull(System.getenv("LLM_KEY"))
        val zone = ZoneId.of("Asia/Shanghai")
        val repository = NoticeParseRepository({ llmClient(config, key) }, Clock.fixed(Instant.parse("2026-10-27T02:00:00Z"), zone), { zone })
        var titles = 0; var times = 0; var locations = 0; var total = 0; var counts = 0; var failures = 0
        for (id in ids) {
            val expected = NoticeValidator.decode(resource("$id.expected.json")).todos
            val actual = repository.parse(resource("$id.txt")).getOrNull()
            if (actual == null) failures++
            if (actual?.size == expected.size) counts++
            val slots = maxOf(expected.size, actual?.size ?: 0)
            total += slots
            repeat(slots) { index ->
                val e = expected.getOrNull(index); val a = actual?.getOrNull(index)
                if (e != null && a != null) {
                    if (e.title == a.title) titles++
                    if (e.dueAt?.let(LocalDateTime::parse) == a.due && e.allDay == a.allDay) times++
                    if (e.location.orEmpty() == a.location) locations++
                }
            }
        }
        // Exact strings, ordered slots, extras and missing items count as mismatches. No raw model text.
        println("Synthetic evaluation: cases=${ids.size}, failures=$failures, correctItemCounts=$counts/${ids.size}")
        println("Exact title=$titles/$total, time+allDay=$times/$total, location=$locations/$total")
    }
    companion object { @JvmStatic fun main(args: Array<String>) { NoticeParseEvalTest().evaluate() } }
}
