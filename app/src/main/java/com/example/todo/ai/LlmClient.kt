package com.example.todo.ai

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okio.Buffer

fun interface LlmClient { suspend fun complete(system: String, user: String): Result<String> }

fun llmClient(config: AiConfiguration, key: String, connectionTest: Boolean = false): LlmClient = when (config.provider) {
    AiProvider.OPENAI_COMPAT -> OpenAiCompatClient(config, key, jsonMode = !connectionTest, maxTokens = if (connectionTest) 8 else 1024)
    AiProvider.ANTHROPIC -> AnthropicClient(config, key, maxTokens = if (connectionTest) 8 else 1024)
}

internal fun endpoint(base: String, provider: AiProvider): HttpUrl {
    val url = base.trim().trimEnd('/').toHttpUrl()
    val segments = url.pathSegments.filter { it.isNotEmpty() }
    val path = when (provider) {
        AiProvider.OPENAI_COMPAT -> if (segments.takeLast(2) == listOf("chat", "completions")) emptyList()
            else if (segments.isEmpty()) listOf("v1", "chat", "completions") else listOf("chat", "completions")
        AiProvider.ANTHROPIC -> if (segments.takeLast(2) == listOf("v1", "messages")) emptyList()
            else if (segments.lastOrNull() == "v1") listOf("messages") else listOf("v1", "messages")
    }
    return url.newBuilder().apply { path.forEach(::addPathSegment) }.build()
}

abstract class HttpLlmClient(protected val config: AiConfiguration, private val suppliedClient: OkHttpClient? = null) : LlmClient {
    private val http = (suppliedClient ?: OkHttpClient()).newBuilder()
        .callTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    protected val json = Json { ignoreUnknownKeys = true }
    protected suspend fun request(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            val buffer = Buffer()
                            val source = it.body.source()
                            while (buffer.size <= 262_144) {
                                if (source.read(buffer, minOf(8_192L, 262_145L - buffer.size)) == -1L) break
                            }
                            if (buffer.size > 262_144) throw AiException(AiFailure.RESPONSE)
                            it.code to buffer.readUtf8()
                        }
                        continuation.resume(result)
                    } catch (e: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(e)
                    }
                }
            })
        }
    protected fun checkStatus(code: Int) {
        if (code !in 200..299) throw AiException(when (code) {
            401, 403 -> AiFailure.AUTH; 404 -> AiFailure.URL; 408 -> AiFailure.TIMEOUT; 429 -> AiFailure.RATE_LIMIT
            in 500..599 -> AiFailure.SERVER; else -> AiFailure.RESPONSE
        })
    }
    protected suspend fun guarded(block: suspend () -> String): Result<String> = try {
        Result.success(block().takeIf { it.isNotBlank() } ?: throw AiException(AiFailure.RESPONSE))
    } catch (e: CancellationException) { throw e }
    catch (e: AiException) { Result.failure(e) }
    catch (_: InterruptedIOException) { Result.failure(AiException(AiFailure.TIMEOUT)) }
    catch (_: IOException) { Result.failure(AiException(AiFailure.NETWORK)) }
    catch (_: Exception) { Result.failure(AiException(AiFailure.RESPONSE)) }
    protected fun JsonObject.body() = toString().toRequestBody("application/json; charset=utf-8".toMediaType())
}

class OpenAiCompatClient(config: AiConfiguration, private val key: String, private val jsonMode: Boolean = true,
    private val maxTokens: Int = 1024, http: OkHttpClient? = null) : HttpLlmClient(config, http) {
    override suspend fun complete(system: String, user: String): Result<String> = guarded {
        suspend fun send(format: Boolean) = request(Request.Builder().url(endpoint(config.baseUrl, AiProvider.OPENAI_COMPAT))
            .header("Authorization", "Bearer $key").post(buildJsonObject {
                put("model", config.model); put("temperature", 0); put("max_tokens", maxTokens)
                putJsonArray("messages") {
                    add(buildJsonObject { put("role", "system"); put("content", system) })
                    add(buildJsonObject { put("role", "user"); put("content", user) })
                }
                if (format) putJsonObject("response_format") { put("type", "json_object") }
            }.body()).build())
        var response = send(jsonMode)
        if (response.first == 400 && jsonMode) response = send(false)
        checkStatus(response.first)
        json.parseToJsonElement(response.second).jsonObject["choices"]!!.jsonArray.first().jsonObject["message"]!!
            .jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: throw AiException(AiFailure.RESPONSE)
    }
}

class AnthropicClient(config: AiConfiguration, private val key: String, private val maxTokens: Int = 1024,
    http: OkHttpClient? = null) : HttpLlmClient(config, http) {
    override suspend fun complete(system: String, user: String): Result<String> = guarded {
        val (code, body) = request(Request.Builder().url(endpoint(config.baseUrl, AiProvider.ANTHROPIC))
            .header("x-api-key", key).header("anthropic-version", "2023-06-01").post(buildJsonObject {
                put("model", config.model); put("max_tokens", maxTokens); put("system", system)
                putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", user) }) }
            }.body()).build())
        checkStatus(code)
        json.parseToJsonElement(body).jsonObject["content"]!!.jsonArray.mapNotNull {
            it.jsonObject.takeIf { block -> block["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.contentOrNull
        }.joinToString("\n")
    }
}
