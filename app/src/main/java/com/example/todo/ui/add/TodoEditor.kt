package com.example.todo.ui.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.todo.ui.components.LineIcons
import java.time.format.DateTimeFormatter
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.platform.LocalConfiguration
import com.example.todo.data.local.TodoEntity
import com.example.todo.data.local.ReminderEntity
import com.example.todo.ui.components.todoSharedBounds
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
    val keyboard = LocalSoftwareKeyboardController.current
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
            Text("记下一件事", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onAi, enabled = !saving) { Text("粘贴通知") }
        }
        MonthCalendar(date?.let(LocalDate::parse), !saving, markedDates) { date = it.toString() }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(title, { if (it.length <= 200) title = it }, label = { Text("标题") },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 30.sp),
            modifier = Modifier.fillMaxWidth().testTag("todo-title").then(if (original == null) Modifier else Modifier.todoSharedBounds(original.id, title = true)).focusRequester(focus), enabled = !saving,
            shape = RoundedCornerShape(4.dp), maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); save() }))
        Spacer(Modifier.height(12.dp))
        EditorField(LineIcons.Calendar, "日期", date?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("yyyy年M月d日", java.util.Locale.CHINA)) } ?: "未设置", !saving) { dateDialog = true }
        EditorField(LineIcons.Clock, "时间", time?.take(5) ?: if (date != null) "全天" else "未设置", !saving) { timeDialog = true }
        EditorField(LineIcons.Location, "地点", location.ifBlank { "未设置" }, !saving) { textDraft = location; textDialog = "地点" }
        EditorField(LineIcons.Note, "备注", note.ifBlank { "未填写" }, !saving) { textDraft = note; textDialog = "备注" }
        if (date != null) {
            EditorField(LineIcons.Bell, "提醒", when { !remind -> "关闭"; time == null -> "%02d:%02d".format(defaultMinute / 60, defaultMinute % 60); offset == 0L -> "准时"; else -> "提前 $offset 分钟" }, !saving) { reminderDialog = true }
            EditorField(LineIcons.Repeat, "重复", when { repeat == "DAILY" -> "每天"; repeat == "MONTHLY" -> "每月"; repeat?.startsWith("WEEKLY:") == true -> "每周 " + repeat!!.substringAfter(':').split(',').joinToString("、") { listOf("一", "二", "三", "四", "五", "六", "日")[it.toInt() - 1] }; else -> "不重复" }, !saving) { repeatDialog = true }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(20.dp))
    }
        Button(onClick = save, enabled = title.trim().isNotEmpty() && !saving, shape = RoundedCornerShape(5.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = pagePadding, vertical = 16.dp).heightIn(min = 54.dp)) {
            Text(if (saving) "正在保存…" else "保存", style = MaterialTheme.typography.titleLarge)
        }
    }
    if (textDialog != null) AlertDialog(onDismissRequest = { textDialog = null }, title = { Text(textDialog!!) },
        text = { OutlinedTextField(textDraft, { textDraft = it }, label = { Text(if (textDialog == "地点") "地点（可选）" else "备注（可选）") },
            modifier = Modifier.fillMaxWidth(), minLines = if (textDialog == "备注") 4 else 1, maxLines = 8) },
        confirmButton = { TextButton(onClick = { if (textDialog == "地点") location = textDraft else note = textDraft; textDialog = null }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { textDialog = null }) { Text("取消") } })
    if (reminderDialog) AlertDialog(onDismissRequest = { reminderDialog = false }, title = { Text("提醒") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("启用提醒", Modifier.weight(1f)); Switch(remind, { remind = it; reminderTouched = true }) }
            if (remind && time == null) Text("全天提醒：%02d:%02d，可在设置中修改".format(defaultMinute / 60, defaultMinute % 60))
            if (remind && time != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0L to "准时", 5L to "提前5分钟", 10L to "提前10分钟", 30L to "提前30分钟", 60L to "提前1小时", 1440L to "提前1天").forEach { (minutes, label) ->
                    FilterChip(selected = offset == minutes, onClick = { offset = minutes; reminderTouched = true }, label = { Text(label) })
                }
                InputChip(selected = offset !in listOf(0L, 5L, 10L, 30L, 60L, 1440L), onClick = { customText = offset.toString(); customOffset = true }, label = { Text("自定义") })
            }
        }
    }, confirmButton = { TextButton(onClick = { reminderDialog = false }) { Text("完成") } })
    if (repeatDialog && date != null) AlertDialog(onDismissRequest = { repeatDialog = false }, title = { Text("重复") }, text = {
        Column {
            listOf(null to "不重复", "DAILY" to "每天", "WEEKLY:${LocalDate.parse(date).dayOfWeek.value}" to "每周", "MONTHLY" to "每月").forEach { (rule, label) ->
                Row(Modifier.fillMaxWidth().clickable { repeat = rule }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(if (label == "每周") repeat?.startsWith("WEEKLY:") == true else repeat == rule, onClick = { repeat = rule }); Text(label)
                }
            }
            if (repeat?.startsWith("WEEKLY:") == true) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val days = repeat!!.substringAfter(':').split(',').map(String::toInt).toSet()
                (1..7).forEach { day -> FilterChip(selected = day in days, onClick = {
                    val updated = if (day in days && days.size > 1) days - day else days + day
                    repeat = "WEEKLY:" + updated.sorted().joinToString(",")
                }, label = { Text(listOf("一", "二", "三", "四", "五", "六", "日")[day - 1]) }) }
            }
        }
    }, confirmButton = { TextButton(onClick = { repeatDialog = false }) { Text("完成") } })
    if (customOffset) AlertDialog(onDismissRequest = { customOffset = false }, title = { Text("提前多少分钟提醒") },
        text = { OutlinedTextField(customText, { customText = it.filter(Char::isDigit).take(6) }, label = { Text("分钟，最多 525600") }, keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)) },
        confirmButton = { TextButton(enabled = customText.toLongOrNull() in 0L..525600L, onClick = { offset = customText.toLong(); reminderTouched = true; customOffset = false }) { Text("确定") } },
        dismissButton = { TextButton(onClick = { customOffset = false }) { Text("取消") } })
    if (dateDialog) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (date?.let(LocalDate::parse) ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton(onClick = {
            picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; dateDialog = false
        }) { Text("确定") } }, dismissButton = { Row { TextButton(onClick = { date = null; time = null; repeat = null; dateDialog = false }) { Text("清除日期") }; TextButton(onClick = { dateDialog = false }) { Text("取消") } } }) { DatePicker(picker) }
    }
    if (timeDialog) {
        val value = time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
        val picker = rememberTimePickerState(initialHour = value.hour, initialMinute = value.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("选择时间") }, text = { TimeInput(picker) },
            confirmButton = { TextButton(onClick = { date = date ?: LocalDate.now().toString(); time = LocalTime.of(picker.hour, picker.minute).toString(); timeDialog = false }) { Text("确定") } },
            dismissButton = { Row { TextButton(onClick = { time = null; timeDialog = false }) { Text("改为全天") }; TextButton(onClick = { timeDialog = false }) { Text("取消") } } })
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
