package com.example.todo.ui.settings

import com.example.todo.R
import com.example.todo.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.todo.ui.components.WheelTimePicker
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.todo.data.settings.*
import com.example.todo.ui.components.LineIcons
import com.example.todo.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: UserSettings, store: SettingsStore, onBack: () -> Unit, onAi: () -> Unit, onReminder: () -> Unit, onAiSettings: () -> Unit = {}, crashLogs: com.example.todo.diagnostics.CrashLogs? = null) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    fun change(block: suspend () -> Unit) { scope.launch { try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = true } } }
    Scaffold(topBar = { TopAppBar(title = { Text(t(R.string.settings)) }, navigationIcon = { IconButton(onClick = onBack) { Icon(LineIcons.Back, t(R.string.back)) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LanguageSetting()
            Text(t(R.string.appearance), style = MaterialTheme.typography.titleMedium)
            listOf("SYSTEM" to t(R.string.system_default), "LIGHT" to t(R.string.light), "DARK" to t(R.string.dark)).forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(settings.theme == value, modifier = Modifier.testTag("theme-$value"), onClick = { change { store.theme(value) } }); Text(label) }
            }
            PaletteSettings(settings) { color -> change { store.accent(color) } }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t(R.string.wallpaper_colors), Modifier.weight(1f)); Switch(settings.dynamicColor, enabled = android.os.Build.VERSION.SDK_INT >= 31, onCheckedChange = { change { store.dynamic(it) } }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t(R.string.reduce_motion), Modifier.weight(1f)); Switch(LocalReducedMotion.current, onCheckedChange = { change { store.motion(it) } }) }
            TextButton(onClick = { change { store.motion(null) } }) { Text(t(R.string.motion_follows_device_performance_automatic)) }
            HorizontalDivider()
            TextButton(onClick = onReminder) { Text(t(R.string.reminder_check)) }
            TextButton(onClick = { timeDialog = true }) { Text(t(R.string.all_day_reminder_time_02d_02d).format(settings.allDayMinute / 60, settings.allDayMinute % 60)) }
            TextButton(onClick = onAi) { Text(t(R.string.ai_text_import)) }
            TextButton(onClick = onAiSettings) { Text(t(R.string.ai_service_and_key)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t(R.string.keep_imported_source_text), Modifier.weight(1f)); Switch(settings.saveSource, onCheckedChange = { change { store.saveSource(it) } }) }
            Text(t(R.string.off_by_default_when_enabled_source_text_is_stored_only_with_the_r), style = MaterialTheme.typography.bodySmall)
            crashLogs?.let { DiagnosticActions(it) }
            Text(t(R.string.tasks_stay_on_this_device_no_account_or_sync), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) Text(t(R.string.could_not_save_settings_try_again), color = MaterialTheme.colorScheme.error)
        }
    }
    if (timeDialog) {
        val picker = rememberTimePickerState(initialHour = settings.allDayMinute / 60, initialMinute = settings.allDayMinute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text(t(R.string.default_all_day_reminder_time)) }, text = { WheelTimePicker(picker.hour, picker.minute, { picker.hour = it }, { picker.minute = it }) },
            confirmButton = { TextButton(onClick = { change { store.allDay(picker.hour * 60 + picker.minute) }; timeDialog = false }) { Text(t(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text(t(R.string.cancel)) } })
    }
}
