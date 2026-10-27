package com.example.todo.ui.ai

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
                    .fold(onSuccess = { message = "连接成功" }, onFailure = { message = it.aiMessage() })
            } else message = "AI 设置已保存"
        } catch (e: CancellationException) { throw e }
        catch (e: IllegalArgumentException) { message = e.message ?: "请检查设置" }
        catch (_: Exception) { message = "配置或密钥保存失败，请重试" }
        finally { busy = false }
    } }
    Scaffold(topBar = { TopAppBar(title = { Text("AI 设置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(LineIcons.Back, "返回") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("通知文本会发送到你填写的服务地址。密钥加密保存在本机。", style = MaterialTheme.typography.bodyMedium)
            AiProvider.entries.forEach { value -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RadioButton(provider == value, enabled = !busy, onClick = { provider = value })
                Text(if (value == AiProvider.OPENAI_COMPAT) "OpenAI 兼容接口" else "Anthropic Messages")
            } }
            OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, placeholder = { Text("https://api.example.com/v1") },
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(model, { model = it }, label = { Text("模型名称") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text("API Key") }, singleLine = true, enabled = !busy,
                supportingText = { Text(saved?.takeIf { it.hasKey }?.let { "已保存 ····${it.keyHint}；留空保留当前密钥" } ?: "尚未保存密钥") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit).take(3) }, label = { Text("超时（秒）") }, singleLine = true,
                enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = initialized && !busy, onClick = { save(false) }) { Text("保存设置") }
                OutlinedButton(enabled = initialized && !busy, onClick = { if (saved?.consentGranted == true) save(true) else consent = true }) { Text("测试连接") }
            }
            if (busy) NoticeSkeleton()
            message?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp)) }
            TextButton(enabled = !busy && saved?.hasKey == true, onClick = { scope.launch {
                try { app.settings.clearAiKey(); key = ""; message = "已删除保存的密钥" }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "删除失败，请重试" }
            } }) { Text("删除已保存的密钥") }
            if (saved?.consentGranted == true) TextButton(enabled = !busy, onClick = { scope.launch {
                try { app.settings.aiConsent(false); message = "已撤回授权，下次请求前将重新询问" }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "授权更新失败，请重试" }
            } }) { Text("撤回 AI 数据发送授权") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (consent) AiConsentDialog(onCancel = { consent = false }, onAgree = {
        scope.launch {
            try { app.settings.aiConsent(true); consent = false; save(true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "授权保存失败，请重试" }
        }
    })
}

@Composable
fun AiConsentDialog(onCancel: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text("使用 AI 识别") },
        text = { Text("通知文本将发送到你配置的模型服务商。请确认服务地址可信，并自行决定哪些内容适合发送。未同意时不会发送。") },
        confirmButton = { TextButton(onClick = onAgree) { Text("同意并继续") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("暂不使用") } })
}
