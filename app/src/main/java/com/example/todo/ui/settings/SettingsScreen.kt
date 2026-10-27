package com.example.todo.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.example.todo.data.settings.*
import com.example.todo.ui.components.LineIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: UserSettings, store: SettingsStore, onBack: () -> Unit, onAi: () -> Unit, onReminder: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    fun change(block: suspend () -> Unit) { scope.launch { try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { error = true } } }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(LineIcons.Back, "返回") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (value, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(settings.theme == value, onClick = { change { store.theme(value) } }); Text(label) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("动态取色", Modifier.weight(1f)); Switch(settings.dynamicColor, onCheckedChange = { change { store.dynamic(it) } }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("动画降级", Modifier.weight(1f)); Switch(settings.reduceMotion == true, onCheckedChange = { change { store.motion(it) } }) }
            TextButton(onClick = { change { store.motion(null) } }) { Text("动画跟随设备性能（自动）") }
            HorizontalDivider()
            TextButton(onClick = onReminder) { Text("提醒自检") }
            TextButton(onClick = { timeDialog = true }) { Text("全天提醒时间：%02d:%02d".format(settings.allDayMinute / 60, settings.allDayMinute % 60)) }
            TextButton(onClick = onAi) { Text("AI 通知识别") }
            Text("离线保存 · 数据仅在本机", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (error) Text("设置保存失败，请重试", color = MaterialTheme.colorScheme.error)
        }
    }
    if (timeDialog) {
        val picker = rememberTimePickerState(initialHour = settings.allDayMinute / 60, initialMinute = settings.allDayMinute % 60, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("默认全天提醒时间") }, text = { TimeInput(picker) },
            confirmButton = { TextButton(onClick = { change { store.allDay(picker.hour * 60 + picker.minute) }; timeDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("取消") } })
    }
}
