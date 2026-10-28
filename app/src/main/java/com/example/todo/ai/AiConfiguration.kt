package com.example.todo.ai

import com.example.todo.R
import com.example.todo.i18n.t
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
            t(R.string.enter_an_https_service_address_without_credentials_or_query_param)
        }
        require(model.trim().isNotEmpty()) { t(R.string.enter_a_model_name) }
        require(timeoutSeconds in 1..120) { t(R.string.timeout_must_be_1_120_seconds) }
        return copy(baseUrl = url.toString().trimEnd('/'), model = model.trim())
    }
    fun keyBinding() = "${provider.name}|${baseUrl.trimEnd('/')}"
}

enum class AiFailure { AUTH, URL, TIMEOUT, NETWORK, RESPONSE, RATE_LIMIT, SERVER, CONFIG, CONSENT }
class AiException(val kind: AiFailure) : Exception(kind.name)
fun Throwable.aiMessage(): String = when ((this as? AiException)?.kind) {
    AiFailure.AUTH -> t(R.string.authentication_failed_check_your_api_key_and_provider_permissions)
    AiFailure.URL -> t(R.string.endpoint_not_found_check_the_base_url_404)
    AiFailure.TIMEOUT -> t(R.string.request_timed_out_retry_or_increase_the_timeout)
    AiFailure.NETWORK -> t(R.string.connection_failed_check_your_network_or_use_local_rules)
    AiFailure.RATE_LIMIT -> t(R.string.rate_limit_or_quota_reached_try_again_later_429)
    AiFailure.SERVER -> t(R.string.the_model_service_is_temporarily_unavailable_try_again_later)
    AiFailure.CONFIG -> t(R.string.ai_configuration_or_key_is_unavailable_save_your_settings_again)
    AiFailure.CONSENT -> t(R.string.confirm_that_text_may_be_sent_to_your_configured_model_provider_f)
    else -> t(R.string.could_not_read_the_model_response_retry_or_use_local_rules)
}
