package com.example.todo.ai

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class LlmClientTest {
    private lateinit var server: MockWebServer
    @Before fun setup() { server = MockWebServer(); server.start() }
    @After fun close() { server.shutdown() }
    private fun config(provider: AiProvider = AiProvider.OPENAI_COMPAT, timeout: Int = 2) =
        AiConfiguration(provider, server.url("/v1/").toString(), "fixture-model", timeout)
    private fun openAi(content: String) = buildJsonObject {
        putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") { put("content", content) } }) }
    }.toString()
    @Test fun successfulJsonModeCarriesOnlyConfiguredHeadersAndParsesChunkedResponse() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody(openAi("{\"todos\":[]}"), 7))
        val result = OpenAiCompatClient(config(), "fixture-key").complete("JSON only", "synthetic notice")
        assertEquals("{\"todos\":[]}", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer fixture-key", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(0, body["temperature"]!!.jsonPrimitive.int)
    }
    @Test fun unsupportedJsonModeRetriesOnceWithoutResponseFormat() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("unsupported"))
        server.enqueue(MockResponse().setBody(openAi("```json\n{\"todos\":[]}\n```")))
        val content = OpenAiCompatClient(config(), "fixture-key").complete("JSON", "notice").getOrThrow()
        assertTrue(NoticeValidator.decode(content).todos.isEmpty())
        assertTrue(server.takeRequest().body.readUtf8().contains("response_format"))
        assertFalse(server.takeRequest().body.readUtf8().contains("response_format"))
        assertEquals(2, server.requestCount)
    }
    @Test fun second400IsNotRetriedAgain() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(400)) }
        assertTrue(OpenAiCompatClient(config(), "k").complete("JSON", "n").isFailure)
        assertEquals(2, server.requestCount)
    }
    @Test fun errorsHaveFixedMessagesWithoutServerBodyOrKey() = runBlocking {
        for ((code, kind) in listOf(401 to AiFailure.AUTH, 403 to AiFailure.AUTH, 404 to AiFailure.URL,
            429 to AiFailure.RATE_LIMIT, 500 to AiFailure.SERVER)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("SECRET_RAW_NOTICE fixture-key"))
            val failure = OpenAiCompatClient(config(), "fixture-key").complete("JSON", "notice").exceptionOrNull()!!
            assertEquals(kind, (failure as AiException).kind)
            assertFalse(failure.aiMessage().contains("SECRET"))
            assertFalse(failure.toString().contains("fixture-key"))
        }
    }
    @Test fun timeoutIsDistinguishedFromOtherFailures() = runBlocking {
        server.enqueue(MockResponse().setBody(openAi("ok")).setBodyDelay(1500, TimeUnit.MILLISECONDS))
        val failure = OpenAiCompatClient(config(timeout = 1), "k").complete("JSON", "n").exceptionOrNull()
        assertEquals(AiFailure.TIMEOUT, (failure as AiException).kind)
    }
    @Test fun nonJsonAndNullContentAreRejected() = runBlocking {
        for (body in listOf("<html>error</html>", "{\"choices\":[{\"message\":{\"content\":null}}]}")) {
            server.enqueue(MockResponse().setBody(body))
            assertEquals(AiFailure.RESPONSE, (OpenAiCompatClient(config(), "k").complete("JSON", "n").exceptionOrNull() as AiException).kind)
        }
    }
    @Test fun oversizedBodyIsBoundedAndRejected() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(262_145)))
        assertEquals(AiFailure.RESPONSE, (OpenAiCompatClient(config(), "k").complete("JSON", "n").exceptionOrNull() as AiException).kind)
    }
    @Test fun redirectsDoNotForwardCredentials() = runBlocking {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", other.url("/stolen")))
            assertTrue(OpenAiCompatClient(config(), "fixture-key").complete("JSON", "n").isFailure)
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }
    @Test fun cancellationPropagatesInsteadOfBecomingNetworkError() = runBlocking {
        server.enqueue(MockResponse().setBody(openAi("ok")).setBodyDelay(2, TimeUnit.SECONDS))
        val job = async { OpenAiCompatClient(config(timeout = 10), "k").complete("JSON", "n") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
    @Test fun anthropicUsesVersionHeaderAndJoinsTextBlocks() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"first"},{"type":"tool_use","id":"ignored"},{"type":"text","text":"second"}]}"""))
        assertEquals("first\nsecond", AnthropicClient(config(AiProvider.ANTHROPIC), "fixture-key").complete("system", "notice").getOrThrow())
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertEquals("fixture-key", request.getHeader("x-api-key"))
        assertNull(request.getHeader("Authorization"))
        assertEquals(1024, Json.parseToJsonElement(request.body.readUtf8()).jsonObject["max_tokens"]!!.jsonPrimitive.int)
    }
    @Test fun endpointNormalizationPreservesProxyPrefixAndFullPaths() {
        for (base in listOf("https://example.com", "https://example.com/", "https://example.com/v1/", "https://example.com/v1/chat/completions/"))
            assertEquals("https://example.com/v1/chat/completions", endpoint(base, AiProvider.OPENAI_COMPAT).toString())
        for (base in listOf("https://example.com", "https://example.com/", "https://example.com/v1/", "https://example.com/v1/messages/"))
            assertEquals("https://example.com/v1/messages", endpoint(base, AiProvider.ANTHROPIC).toString())
        assertEquals("https://example.com/proxy/v1/chat/completions", endpoint("https://example.com/proxy/v1/", AiProvider.OPENAI_COMPAT).toString())
    }
    @Test fun productionConfigRejectsInsecureOrCredentialBearingUrls() {
        for (base in listOf("http://example.com", "https://user:pass@example.com", "https://example.com?key=secret", "https://example.com/#secret")) {
            assertThrows(IllegalArgumentException::class.java) { AiConfiguration(baseUrl = base, model = "m").validated() }
        }
        assertEquals("https://example.com/v1", AiConfiguration(baseUrl = " https://example.com/v1/ ", model = " m ").validated().baseUrl)
    }
}
