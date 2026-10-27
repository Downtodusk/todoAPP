package com.example.todo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.todo.diagnostics.CrashLogs
import kotlinx.coroutines.*
import java.time.LocalDate

@Composable
fun DiagnosticActions(logs: CrashLogs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val report = logs.exportText()
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(report) }
                }
                message = "日志已导出"
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "导出失败，请重新选择保存位置" }
            finally { busy = false }
        }
    }
    HorizontalDivider()
    Text("诊断日志", style = MaterialTheme.typography.titleMedium)
    Text("仅保留最近 5 次崩溃的异常类型和调用位置，不记录待办内容、通知原文、接口地址或密钥。不会自动上传。", style = MaterialTheme.typography.bodySmall)
    TextButton(enabled = !busy, onClick = { export.launch("todo-diagnostics-${LocalDate.now()}.txt") }) { Text("导出日志") }
    TextButton(enabled = !busy, onClick = { scope.launch {
        busy = true
        try { withContext(Dispatchers.IO) { logs.clear() }; message = "本地诊断日志已清除" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = "清除失败，请重试" }
        finally { busy = false }
    } }) { Text("清除诊断日志") }
    message?.let { Text(it) }
}
