package com.example.todo.ui.ai

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.todo.ai.*
import com.example.todo.ui.components.*
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImportScreen(state: ImportState, configuration: AiConfiguration?, onInput: (String) -> Unit,
    onRecognize: (Boolean) -> Unit, onCancel: () -> Unit, onUpdate: (TodoDraft) -> Unit,
    onAdd: () -> Unit, onConsent: () -> Unit, onSettings: () -> Unit, onManual: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var consent by remember { mutableStateOf(false) }
    val busy = state.loading || state.saving
    fun recognize() {
        if (configuration?.configured != true) onSettings()
        else if (!configuration.consentGranted) consent = true
        else onRecognize(false)
    }
    Scaffold(topBar = { TopAppBar(title = { Text("通知导入") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(LineIcons.Back, "返回") }
    }, actions = { TextButton(onClick = onSettings, enabled = !busy) { Text("AI 设置") } }) },
        bottomBar = { if (state.parsed && state.drafts.isNotEmpty()) Surface(shadowElevation = 3.dp) {
            Button(onClick = onAdd, enabled = !busy && state.drafts.any { it.selected },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("import-confirm")) {
                Text(if (state.saving) "正在添加…" else "添加 ${state.drafts.count { it.selected }} 项待办")
            }
        } }) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding().testTag("import-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(state.input, onInput, label = { Text("粘贴通知文本") }, enabled = !busy,
                    minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth().testTag("notice-input"))
                if (prepareNotice(state.input).truncated) Text("识别仅使用整理后的前 2000 字，请确认关键内容未被截断。", color = MaterialTheme.colorScheme.error)
                if (configuration != null && !configuration.configured) Text("先配置模型服务，或手动创建待办。", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        // Read clipboard only in this explicit user action, never on screen entry.
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { onInput(it.toString()) }
                    }) { Text("从剪贴板粘贴") }
                    Button(enabled = !busy && state.input.isNotBlank() && configuration != null, onClick = { recognize() }) { Text(if (state.error == null) "识别通知" else "重试识别") }
                }
            }
            if (state.loading) item { NoticeSkeleton(); TextButton(onClick = onCancel) { Text("取消识别") } }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.loading) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.error != null) TextButton(enabled = !busy && state.input.isNotBlank(), onClick = { onRecognize(true) }) { Text("使用本地规则") }
                    TextButton(enabled = !busy, onClick = { onManual(state.input) }) { Text("手动创建") }
                }
            }
            if (state.parsed) item {
                Text(if (state.local) "本地规则结果 · 请逐项核对" else "识别结果 · 确认后才会保存", style = MaterialTheme.typography.titleMedium)
                if (state.drafts.isEmpty()) Text("未识别到可执行事项。你可以手动创建，原文将带入备注。")
            }
            items(state.drafts, key = { it.id }) { draft -> DraftCard(draft, !state.saving, onUpdate) }
        }
    }
    if (consent) AiConsentDialog(onCancel = { consent = false }, onAgree = { consent = false; onConsent() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftCard(draft: TodoDraft, enabled: Boolean, update: (TodoDraft) -> Unit) {
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    val now = ZonedDateTime.now()
    fun withDue(due: LocalDateTime?, allDay: Boolean = draft.allDay): TodoDraft {
        val next = draft.copy(due = due, allDay = allDay && due != null, invalidTime = false)
        return next.copy(reminder = draft.reminder && due != null && !next.past(now))
    }
    OutlinedCard(Modifier.fillMaxWidth().testTag("draft-${draft.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(draft.selected, { update(draft.copy(selected = it)) }, enabled = enabled)
                Text("加入待办", style = MaterialTheme.typography.titleSmall)
            }
            if (draft.uncertain || draft.invalidTime) Text(if (draft.invalidTime) "时间格式无效，请重新选择" else "信息不确定，请核对标题和时间", color = MaterialTheme.colorScheme.error)
            if (draft.past(now)) Text("时间已过，提醒默认关闭", color = MaterialTheme.colorScheme.error)
            OutlinedTextField(draft.title, { update(draft.copy(title = it.take(200))) }, enabled = enabled, label = { Text("事项标题") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputChip(selected = draft.due != null, enabled = enabled, onClick = { dateDialog = true }, label = { Text(draft.due?.toLocalDate()?.toString() ?: "选择日期") })
                InputChip(selected = draft.due != null && !draft.allDay, enabled = enabled, onClick = { timeDialog = true }, label = { Text(if (draft.allDay || draft.due == null) "选择时间" else draft.due.toLocalTime().toString()) })
                if (draft.due != null) TextButton(enabled = enabled, onClick = { update(withDue(null)) }) { Text("清除时间") }
            }
            if (draft.due != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("全天", Modifier.weight(1f)); Switch(draft.allDay, { update(withDue(draft.due, it)) }, enabled = enabled) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("提醒", Modifier.weight(1f)); Switch(draft.reminder, { update(draft.copy(reminder = it)) }, enabled = enabled) }
            }
            OutlinedTextField(draft.location, { update(draft.copy(location = it)) }, enabled = enabled, label = { Text("地点") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.note, { update(draft.copy(note = it)) }, enabled = enabled, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (draft.due?.toLocalDate() ?: now.toLocalDate()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { millis ->
                val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                update(withDue(day.atTime(draft.due?.toLocalTime() ?: LocalTime.MIDNIGHT), draft.allDay || draft.due == null))
            }; dateDialog = false
        }) { Text("确定") } }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("取消") } }) { DatePicker(picker) }
    }
    if (timeDialog) {
        val picker = rememberTimePickerState(initialHour = draft.due?.hour ?: 9, initialMinute = draft.due?.minute ?: 0, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("事项时间") }, text = { TimeInput(picker) },
            confirmButton = { TextButton(onClick = { update(withDue((draft.due?.toLocalDate() ?: now.toLocalDate()).atTime(picker.hour, picker.minute), false)); timeDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("取消") } })
    }
}
