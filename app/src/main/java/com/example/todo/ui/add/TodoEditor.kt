package com.example.todo.ui.add

import com.example.todo.R
import com.example.todo.i18n.t
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.todo.ui.components.LineIcons
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.todo.ui.components.WheelTimePicker
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.local.ReminderEntity
import java.time.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoEditor(original: TodoEntity?, saving: Boolean, error: String?, onSave: (TodoEntity, List<ReminderEntity>?) -> Unit,
    onAi: () -> Unit, modifier: Modifier = Modifier, quick: Boolean = false,
    reminders: List<ReminderEntity> = emptyList(), defaultMinute: Int = 540, initialNote: String = "", markedDates: Set<LocalDate> = emptySet()) {
    val pagePadding = if (LocalConfiguration.current.screenWidthDp < 384) 12.dp else 24.dp
    val zone = ZoneId.systemDefault()
    val initialDate = original?.allDayDate ?: original?.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
    val initialTime = original?.dueAt?.takeIf { original.allDay.not() }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().toString() }
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title ?: "") }
    var note by rememberSaveable(original?.id) { mutableStateOf(original?.note ?: initialNote) }
    var location by rememberSaveable(original?.id) { mutableStateOf(original?.location ?: "") }
    var date by rememberSaveable(original?.id) { mutableStateOf(initialDate) }
    var time by rememberSaveable(original?.id) { mutableStateOf(initialTime) }
    var repeat by rememberSaveable(original?.id) { mutableStateOf(original?.repeatRule) }
    var remind by rememberSaveable(original?.id) { mutableStateOf(if (original == null) true else reminders.isNotEmpty()) }
    var reminderTouched by rememberSaveable(original?.id) { mutableStateOf(false) }
    var offset by rememberSaveable(original?.id) { mutableStateOf(reminders.firstOrNull()?.let { ((original?.dueAt ?: it.triggerAt) - it.triggerAt) / 60_000 } ?: 0L) }
    var customOffset by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    var reminderDialog by rememberSaveable { mutableStateOf(false) }
    var repeatDialog by rememberSaveable { mutableStateOf(false) }
    var textDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var textDraft by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val save = {
        if (title.trim().isNotEmpty() && !saving) {
            val localDate = date?.let(LocalDate::parse)
            val allDay = localDate != null && time == null
            val due = localDate?.atTime(time?.let(LocalTime::parse) ?: LocalTime.MIDNIGHT)?.atZone(zone)?.toInstant()?.toEpochMilli()
            val updated = (original ?: TodoEntity(title = title)).copy(title = title, note = note.ifBlank { null }, location = location.ifBlank { null },
                dueAt = due, allDay = allDay, allDayDate = if (allDay) date else null, repeatRule = if (date == null) null else repeat)
            val newReminders = when {
                due == null -> emptyList()
                original != null && !reminderTouched && original.allDay == allDay -> null
                !remind -> emptyList()
                else -> listOf(ReminderEntity(todoId = original?.id ?: 0, triggerAt = if (allDay)
                    localDate.atTime(LocalTime.of(defaultMinute / 60, defaultMinute % 60)).atZone(zone).toInstant().toEpochMilli()
                    else due - offset.coerceAtLeast(0) * 60_000, allDayMinute = if (allDay) defaultMinute else null))
            }
            onSave(updated, newReminders)
        }
    }
    Column(modifier.fillMaxSize().imePadding()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pagePadding), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (quick) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t(R.string.a_new_task), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onAi, enabled = !saving) { Text(t(R.string.paste_text_188)) }
        }
        MonthCalendar(date?.let(LocalDate::parse), !saving, markedDates,
            onMonthClick = { dateDialog = true },
            onClear = { date = null; time = null; repeat = null }) { date = it.toString() }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(title, { if (it.length <= 200) title = it }, label = { Text(t(R.string.title)) },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
            modifier = Modifier.fillMaxWidth().testTag("todo-title").focusRequester(focus), enabled = !saving,
            shape = RoundedCornerShape(4.dp), maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
        Spacer(Modifier.height(12.dp))
        EditorField(LineIcons.Clock, t(R.string.time), time?.take(5) ?: if (date != null) t(R.string.all_day) else t(R.string.not_set), !saving) { timeDialog = true }
        EditorField(LineIcons.Location, t(R.string.location), location.ifBlank { t(R.string.not_set) }, !saving) { textDraft = location; textDialog = "location" }
        EditorField(LineIcons.Note, t(R.string.notes), note.ifBlank { t(R.string.empty) }, !saving) { textDraft = note; textDialog = "note" }
        if (date != null) {
            EditorField(LineIcons.Bell, t(R.string.reminder), when { !remind -> t(R.string.off_193); time == null -> "%02d:%02d".format(defaultMinute / 60, defaultMinute % 60); offset == 0L -> t(R.string.on_time); else -> t(R.string.reminder_early, offset) }, !saving) { reminderDialog = true }
            EditorField(LineIcons.Repeat, t(R.string.repeat), when { repeat == "DAILY" -> t(R.string.daily); repeat == "MONTHLY" -> t(R.string.monthly); repeat?.startsWith("WEEKLY:") == true -> t(R.string.weekly) + repeat!!.substringAfter(':').split(',').joinToString(t(R.string.weekday_separator)) { listOf(t(R.string.mon), t(R.string.tue), t(R.string.wed), t(R.string.thu), t(R.string.fri), t(R.string.sat), t(R.string.sun))[it.toInt() - 1] }; else -> t(R.string.does_not_repeat) }, !saving) { repeatDialog = true }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
    }
        Button(onClick = save, enabled = title.trim().isNotEmpty() && !saving, shape = RoundedCornerShape(5.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = pagePadding, vertical = 16.dp).heightIn(min = 54.dp)) {
            Text(if (saving) t(R.string.saving) else t(R.string.save), style = MaterialTheme.typography.titleLarge)
        }
    }
    if (textDialog != null) AlertDialog(onDismissRequest = { textDialog = null }, title = { Text(if (textDialog == "location") t(R.string.location) else t(R.string.notes)) },
        text = { OutlinedTextField(textDraft, { textDraft = it }, label = { Text(if (textDialog == "location") t(R.string.location_optional) else t(R.string.notes_optional)) },
            modifier = Modifier.fillMaxWidth(), minLines = if (textDialog == "note") 4 else 1, maxLines = 8) },
        confirmButton = { TextButton(onClick = { if (textDialog == "location") location = textDraft else note = textDraft; textDialog = null }) { Text(t(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { textDialog = null }) { Text(t(R.string.cancel)) } })
    if (reminderDialog) AlertDialog(onDismissRequest = { reminderDialog = false }, title = { Text(t(R.string.reminder)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(t(R.string.enable_reminder), Modifier.weight(1f)); Switch(remind, { remind = it; reminderTouched = true }) }
            if (remind && time == null) Text(t(R.string.all_day_reminder_02d_02d_change_this_in_settings).format(defaultMinute / 60, defaultMinute % 60))
            if (remind && time != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0L to t(R.string.on_time), 5L to t(R.string.before_5_min_before), 10L to t(R.string.before_10_min_before), 30L to t(R.string.before_30_min_before), 60L to t(R.string.before_1_hour_before), 1440L to t(R.string.before_1_day_before)).forEach { (minutes, label) ->
                    FilterChip(selected = offset == minutes, onClick = { offset = minutes; reminderTouched = true }, label = { Text(label) })
                }
                InputChip(selected = offset !in listOf(0L, 5L, 10L, 30L, 60L, 1440L), onClick = { customText = offset.toString(); customOffset = true }, label = { Text(t(R.string.custom)) })
            }
        }
    }, confirmButton = { TextButton(onClick = { reminderDialog = false }) { Text(t(R.string.complete)) } })
    if (repeatDialog && date != null) AlertDialog(onDismissRequest = { repeatDialog = false }, title = { Text(t(R.string.repeat)) }, text = {
        Column {
            listOf(null to t(R.string.does_not_repeat), "DAILY" to t(R.string.daily), "WEEKLY:${LocalDate.parse(date).dayOfWeek.value}" to t(R.string.weekly_211), "MONTHLY" to t(R.string.monthly)).forEach { (rule, label) ->
                Row(Modifier.fillMaxWidth().clickable { repeat = rule }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(if (label == t(R.string.weekly_211)) repeat?.startsWith("WEEKLY:") == true else repeat == rule, onClick = { repeat = rule }); Text(label)
                }
            }
            if (repeat?.startsWith("WEEKLY:") == true) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val days = repeat!!.substringAfter(':').split(',').map(String::toInt).toSet()
                (1..7).forEach { day -> FilterChip(selected = day in days, onClick = {
                    val updated = if (day in days && days.size > 1) days - day else days + day
                    repeat = "WEEKLY:" + updated.sorted().joinToString(",")
                }, label = { Text(listOf(t(R.string.mon), t(R.string.tue), t(R.string.wed), t(R.string.thu), t(R.string.fri), t(R.string.sat), t(R.string.sun))[day - 1]) }) }
            }
        }
    }, confirmButton = { TextButton(onClick = { repeatDialog = false }) { Text(t(R.string.complete)) } })
    if (customOffset) AlertDialog(onDismissRequest = { customOffset = false }, title = { Text(t(R.string.minutes_before_the_task)) },
        text = { OutlinedTextField(customText, { customText = it.filter(Char::isDigit).take(6) }, label = { Text(t(R.string.minutes_up_to_525600)) }, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) },
        confirmButton = { TextButton(enabled = customText.toLongOrNull() in 0L..525600L, onClick = { offset = customText.toLong(); reminderTouched = true; customOffset = false }) { Text(t(R.string.ok)) } },
        dismissButton = { TextButton(onClick = { customOffset = false }) { Text(t(R.string.cancel)) } })
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (date?.let(LocalDate::parse) ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; dateDialog = false
        }) { Text(t(R.string.ok)) } }, dismissButton = { Row { TextButton(onClick = { date = null; time = null; repeat = null; dateDialog = false }) { Text(t(R.string.clear_date)) }; TextButton(onClick = { dateDialog = false }) { Text(t(R.string.cancel)) } } }) { DatePicker(picker) }
    }
    if (timeDialog) {
        val value = time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
        val picker = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text(t(R.string.choose_time)) }, text = { WheelTimePicker(picker.hour, picker.minute, { picker.hour = it }, { picker.minute = it }) },
            confirmButton = { TextButton(onClick = { date = date ?: LocalDate.now().toString(); time = LocalTime.of(picker.hour, picker.minute).toString(); timeDialog = false }) { Text(t(R.string.ok)) } },
            dismissButton = { Row { TextButton(onClick = { time = null; timeDialog = false }) { Text(t(R.string.make_all_day)) }; TextButton(onClick = { timeDialog = false }) { Text(t(R.string.cancel)) } } })
    }
}

@Composable
private fun EditorField(icon: ImageVector, label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.weight(1f))
        Icon(LineIcons.Forward, null, Modifier.padding(start = 12.dp).size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(thickness = .5.dp)
}
