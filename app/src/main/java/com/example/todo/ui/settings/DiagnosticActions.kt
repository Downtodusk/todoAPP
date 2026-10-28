package com.example.todo.ui.settings

import com.example.todo.R
import com.example.todo.i18n.t
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
                message = t(R.string.logs_exported)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = t(R.string.could_not_export_choose_another_location) }
            finally { busy = false }
        }
    }
    HorizontalDivider()
    Text(t(R.string.diagnostic_logs), style = MaterialTheme.typography.titleMedium)
    Text(t(R.string.only_the_exception_type_and_code_location_of_the_last_5_crashes_a), style = MaterialTheme.typography.bodySmall)
    TextButton(enabled = !busy, onClick = { export.launch("todo-diagnostics-${LocalDate.now()}.txt") }) { Text(t(R.string.export_logs)) }
    TextButton(enabled = !busy, onClick = { scope.launch {
        busy = true
        try { withContext(Dispatchers.IO) { logs.clear() }; message = t(R.string.local_diagnostic_logs_cleared) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { message = t(R.string.could_not_clear_try_again) }
        finally { busy = false }
    } }) { Text(t(R.string.clear_diagnostic_logs)) }
    message?.let { Text(it) }
}
