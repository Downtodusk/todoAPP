package com.example.todo.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class AiProvider { OPENAI_COMPAT, ANTHROPIC }
data class AiConfiguration(
    val provider: AiProvider = AiProvider.OPENAI_COMPAT,
    val baseUrl: String = "",
    val model: String = "",
    val timeoutSeconds: Int = 20,
    val hasKey: Boolean = false,
    val keyHint: String = "",
    val consentGranted: Boolean = false,
) {
    val configured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank() && hasKey
    fun validated(): AiConfiguration {
        val url = baseUrl.trim().toHttpUrlOrNull()
        require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "请输入 HTTPS 服务地址，不要在地址中填写密钥或查询参数"
        }
        require(model.trim().isNotEmpty()) { "请填写模型名称" }
        require(timeoutSeconds in 1..120) { "超时需要设置为 1–120 秒" }
        return copy(baseUrl = url.toString().trimEnd('/'), model = model.trim())
    }
    fun keyBinding() = "${provider.name}|${baseUrl.trimEnd('/')}"
}

enum class AiFailure { AUTH, URL, TIMEOUT, NETWORK, RESPONSE, RATE_LIMIT, SERVER, CONFIG, CONSENT }
class AiException(val kind: AiFailure) : Exception(kind.name)
fun Throwable.aiMessage(): String = when ((this as? AiException)?.kind) {
    AiFailure.AUTH -> "鉴权失败，请检查 API Key 和服务商权限（401/403）"
    AiFailure.URL -> "接口地址不存在，请检查 Base URL（404）"
    AiFailure.TIMEOUT -> "请求超时，请重试或调整超时时间"
    AiFailure.NETWORK -> "网络连接失败，请检查网络或使用本地规则"
    AiFailure.RATE_LIMIT -> "服务商暂时限流或额度不足，请稍后重试（429）"
    AiFailure.SERVER -> "模型服务暂时不可用，请稍后重试"
    AiFailure.CONFIG -> "AI 配置或密钥不可用，请重新保存设置"
    AiFailure.CONSENT -> "请先确认将通知文本发送到你配置的模型服务商"
    else -> "模型返回的内容无法识别，请重试或使用本地规则"
}
