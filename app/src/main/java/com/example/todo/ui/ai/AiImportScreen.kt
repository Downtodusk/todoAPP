package com.example.todo.ui.ai

import com.example.todo.R
import com.example.todo.i18n.t
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.example.todo.ui.components.WheelTimePicker
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
    Scaffold(topBar = { TopAppBar(title = { Text(t(R.string.import_text)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(LineIcons.Back, t(R.string.back)) }
    }, actions = { TextButton(onClick = onSettings, enabled = !busy) { Text(t(R.string.ai_settings)) } }) },
        bottomBar = { if (state.parsed && state.drafts.isNotEmpty()) Surface(shadowElevation = 3.dp) {
            Button(onClick = onAdd, enabled = !busy && state.drafts.any { it.selected },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("import-confirm")) {
                Text(if (state.saving) t(R.string.adding) else t(R.string.add_task_count, state.drafts.count { it.selected }))
            }
        } }) { padding ->
        LazyColumn(Modifier.padding(padding).imePadding().testTag("import-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(state.input, onInput, label = { Text(t(R.string.paste_text)) }, enabled = !busy,
                    minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth().testTag("notice-input"))
                if (prepareNotice(state.input).truncated) Text(t(R.string.only_the_first_2_000_characters_of_cleaned_text_are_used_check_th), color = MaterialTheme.colorScheme.error)
                if (configuration != null && !configuration.configured) Text(t(R.string.configure_a_model_service_first_or_create_a_task_manually), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = {
                        // Read clipboard only in this explicit user action, never on screen entry.
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { onInput(it.toString()) }
                    }) { Text(t(R.string.paste_from_clipboard)) }
                    Button(enabled = !busy && state.input.isNotBlank() && configuration != null, onClick = { recognize() }) { Text(if (state.error == null) t(R.string.identify_tasks) else t(R.string.retry_import)) }
                }
            }
            if (state.loading) item { NoticeSkeleton(); TextButton(onClick = onCancel) { Text(t(R.string.cancel_import)) } }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.loading) item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.error != null) TextButton(enabled = !busy && state.input.isNotBlank(), onClick = { onRecognize(true) }) { Text(t(R.string.use_local_rules_chinese)) }
                    TextButton(enabled = !busy, onClick = { onManual(state.input) }) { Text(t(R.string.create_manually)) }
                }
            }
            if (state.parsed) item {
                Text(if (state.local) t(R.string.local_results_review_each_task) else t(R.string.results_saved_only_after_confirmation), style = MaterialTheme.typography.titleMedium)
                if (state.drafts.isEmpty()) Text(t(R.string.no_actionable_tasks_found_you_can_create_one_manually_with_the_or))
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
                Text(t(R.string.include_task), style = MaterialTheme.typography.titleSmall)
            }
            if (draft.uncertain || draft.invalidTime) Text(if (draft.invalidTime) t(R.string.invalid_time_choose_it_again) else t(R.string.some_details_are_uncertain_check_the_title_and_time), color = MaterialTheme.colorScheme.error)
            if (draft.past(now)) Text(t(R.string.this_time_has_passed_reminders_are_off_by_default), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(draft.title, { update(draft.copy(title = it.take(200))) }, enabled = enabled, label = { Text(t(R.string.task_title)) }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InputChip(selected = draft.due != null, enabled = enabled, onClick = { dateDialog = true }, label = { Text(draft.due?.toLocalDate()?.toString() ?: t(R.string.choose_date)) })
                InputChip(selected = draft.due != null && !draft.allDay, enabled = enabled, onClick = { timeDialog = true }, label = { Text(if (draft.allDay || draft.due == null) t(R.string.choose_time) else draft.due.toLocalTime().toString()) })
                if (draft.due != null) TextButton(enabled = enabled, onClick = { update(withDue(null)) }) { Text(t(R.string.clear_time)) }
            }
            if (draft.due != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(t(R.string.all_day), Modifier.weight(1f)); Switch(draft.allDay, { update(withDue(draft.due, it)) }, enabled = enabled) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(t(R.string.reminder), Modifier.weight(1f)); Switch(draft.reminder, { update(draft.copy(reminder = it)) }, enabled = enabled) }
            }
            OutlinedTextField(draft.location, { update(draft.copy(location = it)) }, enabled = enabled, label = { Text(t(R.string.location)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.note, { update(draft.copy(note = it)) }, enabled = enabled, label = { Text(t(R.string.notes)) }, modifier = Modifier.fillMaxWidth())
        }
    }
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (draft.due?.toLocalDate() ?: now.toLocalDate()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { millis ->
                val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                update(withDue(day.atTime(draft.due?.toLocalTime() ?: LocalTime.MIDNIGHT), draft.allDay || draft.due == null))
            }; dateDialog = false
        }) { Text(t(R.string.ok)) } }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text(t(R.string.cancel)) } }) { DatePicker(picker) }
    }
    if (timeDialog) {
        val picker = rememberTimePickerState(initialHour = draft.due?.hour ?: 9, initialMinute = draft.due?.minute ?: 0, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text(t(R.string.task_time)) }, text = { WheelTimePicker(picker.hour, picker.minute, { picker.hour = it }, { picker.minute = it }) },
            confirmButton = { TextButton(onClick = { update(withDue((draft.due?.toLocalDate() ?: now.toLocalDate()).atTime(picker.hour, picker.minute), false)); timeDialog = false }) { Text(t(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text(t(R.string.cancel)) } })
    }
}
