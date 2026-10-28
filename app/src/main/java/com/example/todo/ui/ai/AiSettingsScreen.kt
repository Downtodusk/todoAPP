package com.example.todo.ui.ai

import com.example.todo.R
import com.example.todo.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todo.AppContainer
import com.example.todo.ai.*
import com.example.todo.ui.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(app: AppContainer, onBack: () -> Unit) {
    val saved by app.settings.ai.collectAsStateWithLifecycle(initialValue = null)
    var initialized by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf(AiProvider.OPENAI_COMPAT) }
    var baseUrl by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("20") }
    var key by remember { mutableStateOf("") } // Never save plaintext in saved-instance state.
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(saved) { saved?.let { if (!initialized) {
        provider = it.provider; baseUrl = it.baseUrl; model = it.model; timeout = it.timeoutSeconds.toString(); initialized = true
    } } }
    fun save(test: Boolean) { scope.launch {
        busy = true; message = null
        try {
            val config = AiConfiguration(provider, baseUrl, model, timeout.toIntOrNull() ?: 0).validated()
            app.settings.saveAi(config, key, app.secrets)
            key = ""
            if (test) {
                val current = app.settings.ai.first()
                if (!current.consentGranted) throw AiException(AiFailure.CONSENT)
                llmClient(current, app.settings.aiKey(current, app.secrets), connectionTest = true)
                    .complete("Reply briefly with pong.", "ping")
                    .fold(onSuccess = { message = t(R.string.connected_successfully) }, onFailure = { message = it.aiMessage() })
            } else message = t(R.string.ai_settings_saved)
        } catch (e: CancellationException) { throw e }
        catch (e: IllegalArgumentException) { message = e.message ?: t(R.string.check_your_settings) }
        catch (_: Exception) { message = t(R.string.could_not_save_configuration_or_key_try_again) }
        finally { busy = false }
    } }
    Scaffold(topBar = { TopAppBar(title = { Text(t(R.string.ai_settings)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(LineIcons.Back, t(R.string.back)) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t(R.string.text_will_be_sent_to_the_service_address_you_enter_your_key_is_en), style = MaterialTheme.typography.bodyMedium)
            AiProvider.entries.forEach { value -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(provider == value, enabled = !busy, onClick = { provider = value })
                Text(if (value == AiProvider.OPENAI_COMPAT) t(R.string.openai_compatible) else "Anthropic Messages")
            } }
            OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, placeholder = { Text("https://api.example.com/v1") },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(model, { model = it }, label = { Text(t(R.string.model_name)) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true, enabled = !busy,
                supportingText = { Text(saved?.takeIf { it.hasKey }?.let { t(R.string.saved_key_hint, it.keyHint) } ?: t(R.string.no_saved_key)) },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit).take(3) }, label = { Text(t(R.string.timeout_seconds)) }, singleLine = true,
                enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = initialized && !busy, onClick = { save(false) }) { Text(t(R.string.save_settings)) }
                OutlinedButton(enabled = initialized && !busy, onClick = { if (saved?.consentGranted == true) save(true) else consent = true }) { Text(t(R.string.test_connection)) }
            }
            if (busy) NoticeSkeleton()
            message?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            TextButton(enabled = !busy && saved?.hasKey == true, onClick = { scope.launch {
                try { app.settings.clearAiKey(); key = ""; message = t(R.string.saved_key_deleted) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = t(R.string.could_not_delete_try_again) }
            } }) { Text(t(R.string.delete_saved_key)) }
            if (saved?.consentGranted == true) TextButton(enabled = !busy, onClick = { scope.launch {
                try { app.settings.aiConsent(false); message = t(R.string.consent_withdrawn_you_will_be_asked_again_before_the_next_request) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = t(R.string.could_not_update_consent_try_again) }
            } }) { Text(t(R.string.withdraw_ai_data_consent)) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (consent) AiConsentDialog(onCancel = { consent = false }, onAgree = {
        scope.launch {
            try { app.settings.aiConsent(true); consent = false; save(true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = t(R.string.could_not_save_consent_try_again) }
        }
    })
}

@Composable
fun AiConsentDialog(onCancel: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text(t(R.string.use_ai_to_identify_tasks)) },
        text = { Text(t(R.string.text_will_be_sent_to_your_configured_model_provider_verify_the_se)) },
        confirmButton = { TextButton(onClick = onAgree) { Text(t(R.string.agree_and_continue)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(t(R.string.not_now)) } })
}
