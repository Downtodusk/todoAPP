package com.example.todo.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.app.LocaleManagerCompat
import com.example.todo.R
import java.util.Locale

/** Application-scoped resources also localize workers and notification actions.
 * Refresh on activity/application configuration changes; never retain an Activity.
 */
object AppText {
    @Volatile private var resources: Resources? = null

    fun refresh(context: Context) {
        val application = context.applicationContext
        // AppCompat applies the Activity override before asynchronously persisting it on API 26–32.
        // Reading its configured Resources here avoids showing the previous language during recreation.
        val locale = if (context is Activity) context.resources.configuration.locales[0] else {
            val requested = LocaleManagerCompat.getApplicationLocales(application)
            requested[0] ?: LocaleManagerCompat.getSystemLocales(application)[0] ?: Locale.ENGLISH
        }
        val configuration = Configuration(application.resources.configuration).apply { setLocale(locale) }
        resources = application.createConfigurationContext(configuration).resources
    }

    fun locale(): Locale = resources?.configuration?.locales?.get(0) ?: Locale.ENGLISH

    fun text(@StringRes id: Int, vararg args: Any): String = resources?.let {
        if (args.isEmpty()) it.getString(id) else it.getString(id, *args)
    } ?: domainFallback(id)

    // Domain validation also runs in plain JVM tests, without an Android Application.
    private fun domainFallback(@StringRes id: Int): String = when (id) {
        R.string.ai_configuration_or_key_is_unavailable_save_your_settings_again -> "AI configuration or key is unavailable. Save your settings again."
        R.string.authentication_failed_check_your_api_key_and_provider_permissions -> "Authentication failed. Check your API key and provider permissions (401/403)."
        R.string.confirm_that_text_may_be_sent_to_your_configured_model_provider_f -> "Confirm that text may be sent to your configured model provider first."
        R.string.connection_failed_check_your_network_or_use_local_rules -> "Connection failed. Check your network or use local rules."
        R.string.could_not_read_the_model_response_retry_or_use_local_rules -> "Could not read the model response. Retry or use local rules."
        R.string.endpoint_not_found_check_the_base_url_404 -> "Endpoint not found. Check the base URL (404)."
        R.string.enter_a_model_name -> "Enter a model name"
        R.string.enter_an_api_key_when_configuring_or_changing_the_service_address -> "Enter an API key when configuring or changing the service address"
        R.string.enter_an_https_service_address_without_credentials_or_query_param -> "Enter an HTTPS service address without credentials or query parameters"
        R.string.keys_cannot_contain_line_breaks_or_control_characters -> "Keys cannot contain line breaks or control characters"
        R.string.rate_limit_or_quota_reached_try_again_later_429 -> "Rate limit or quota reached. Try again later (429)."
        R.string.request_timed_out_retry_or_increase_the_timeout -> "Request timed out. Retry or increase the timeout."
        R.string.select_at_least_one_task -> "Select at least one task"
        R.string.set_a_date_before_repeating_a_task -> "Set a date before repeating a task"
        R.string.task_deleted -> "Task deleted"
        R.string.the_model_service_is_temporarily_unavailable_try_again_later -> "The model service is temporarily unavailable. Try again later."
        R.string.the_title_must_contain_1_200_characters -> "The title must contain 1–200 characters"
        R.string.this_task_changed_reopen_it_before_editing -> "This task changed. Reopen it before editing."
        R.string.timeout_must_be_1_120_seconds -> "Timeout must be 1–120 seconds"
        R.string.invalid_repeat_days -> "Invalid repeat weekdays"
        R.string.invalid_repeat_rule -> "Invalid repeat rule"
        else -> error("AppText must be initialized by Application.onCreate")
    }
}

fun t(@StringRes id: Int, vararg args: Any): String = AppText.text(id, *args)
fun appLocale(): Locale = AppText.locale()
