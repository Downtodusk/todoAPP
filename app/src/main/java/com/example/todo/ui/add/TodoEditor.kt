package com.example.todo.ui.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.todo.data.local.TodoEntity
import java.time.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoEditor(original: TodoEntity?, saving: Boolean, error: String?, onSave: (TodoEntity) -> Unit,
    onAi: () -> Unit, modifier: Modifier = Modifier, quick: Boolean = false) {
    val zone = ZoneId.systemDefault()
    val initialDate = original?.allDayDate ?: original?.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().toString() }
    val initialTime = original?.dueAt?.takeIf { original.allDay.not() }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().toString() }
    var title by rememberSaveable(original?.id) { mutableStateOf(original?.title ?: "") }
    var note by rememberSaveable(original?.id) { mutableStateOf(original?.note ?: "") }
    var location by rememberSaveable(original?.id) { mutableStateOf(original?.location ?: "") }
    var date by rememberSaveable(original?.id) { mutableStateOf(initialDate) }
    var time by rememberSaveable(original?.id) { mutableStateOf(initialTime) }
    var repeat by rememberSaveable(original?.id) { mutableStateOf(original?.repeatRule) }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val save = {
        if (title.trim().isNotEmpty() && !saving) {
            val localDate = date?.let(LocalDate::parse)
            val allDay = localDate != null && time == null
            val due = localDate?.atTime(time?.let(LocalTime::parse) ?: LocalTime.MIDNIGHT)?.atZone(zone)?.toInstant()?.toEpochMilli()
            onSave((original ?: TodoEntity(title = title)).copy(title = title, note = note.ifBlank { null }, location = location.ifBlank { null },
                dueAt = due, allDay = allDay, allDayDate = if (allDay) date else null, repeatRule = if (date == null) null else repeat))
        }
    }
    LaunchedEffect(quick) { if (quick) { focus.requestFocus(); keyboard?.show() } }
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (quick) Text("记下一件事", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { if (it.length <= 200) title = it }, label = { Text("标题") },
            singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus), enabled = !saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = date == LocalDate.now().toString(), onClick = { date = LocalDate.now().toString() }, label = { Text("今天") })
            FilterChip(selected = date == LocalDate.now().plusDays(1).toString(), onClick = { date = LocalDate.now().plusDays(1).toString() }, label = { Text("明天") })
            InputChip(selected = date != null, onClick = { dateDialog = true }, label = { Text(date ?: "选日期") })
            InputChip(selected = time != null, onClick = { timeDialog = true }, label = { Text(time?.take(5) ?: "选时间") })
            if (date != null) InputChip(selected = false, onClick = { date = null; time = null; repeat = null }, label = { Text("清除日期") })
            if (time != null) InputChip(selected = false, onClick = { time = null }, label = { Text("改为全天") })
        }
        if (date != null) {
            Text("重复", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "不重复", "DAILY" to "每天", "WEEKLY:${LocalDate.parse(date).dayOfWeek.value}" to "每周", "MONTHLY" to "每月").forEach { (rule, label) ->
                    FilterChip(selected = if (label == "每周") repeat?.startsWith("WEEKLY:") == true else repeat == rule, onClick = { repeat = rule }, label = { Text(label) })
                }
            }
            if (repeat?.startsWith("WEEKLY:") == true) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val days = repeat!!.substringAfter(':').split(',').map(String::toInt).toSet()
                (1..7).forEach { day -> FilterChip(selected = day in days, onClick = {
                    val updated = if (day in days && days.size > 1) days - day else days + day
                    repeat = "WEEKLY:" + updated.sorted().joinToString(",")
                }, label = { Text(listOf("一", "二", "三", "四", "五", "六", "日")[day - 1]) }) }
            }
        }
        OutlinedTextField(location, { location = it }, label = { Text("地点（可选）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        if (quick) TextButton(onClick = onAi) { Text("粘贴通知") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = save, enabled = title.trim().isNotEmpty() && !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (saving) "正在保存…" else "保存") }
    }
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (date?.let(LocalDate::parse) ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; dateDialog = false
        }) { Text("确定") } }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("取消") } }) { DatePicker(picker) }
    }
    if (timeDialog) {
        val value = time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
        val picker = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("选择时间") }, text = { TimeInput(picker) },
            confirmButton = { TextButton(onClick = { date = date ?: LocalDate.now().toString(); time = LocalTime.of(picker.hour, picker.minute).toString(); timeDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { timeDialog = false }) { Text("取消") } })
    }
}
