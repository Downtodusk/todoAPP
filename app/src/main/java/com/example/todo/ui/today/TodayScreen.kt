package com.example.todo.ui.today

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.todo.data.local.TodoEntity
import com.example.todo.domain.*
import com.example.todo.ui.components.LineIcons
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(state: TodoListState, today: LocalDate, zone: ZoneId, all: Boolean,
    onDetail: (Long) -> Unit, onComplete: (TodoEntity) -> Unit, onAdd: () -> Unit,
    onAll: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit) {
    var selectedValue by rememberSaveable { mutableStateOf(today.toString()) }
    var lastToday by rememberSaveable { mutableStateOf(today.toString()) }
    LaunchedEffect(today) { if (selectedValue == lastToday) selectedValue = today.toString(); lastToday = today.toString() }
    val selected = LocalDate.parse(selectedValue)
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val agenda = remember(state.todos, selected, today, zone) { partitionAgenda(state.todos, selected, today, zone) }
    Scaffold(
        floatingActionButton = { FloatingActionButton(onClick = onAdd, shape = RoundedCornerShape(16.dp)) { Icon(LineIcons.Add, "添加待办") } },
        topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (all) IconButton(onClick = onBack) { Icon(LineIcons.Back, "返回") }
            else TextButton(onClick = onAll) { Text("全部待办") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(LineIcons.Settings, "设置") }
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp)) {
            item {
                Text(if (all) "全部待办" else if (selected == today) "今天" else "日程", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp))
                Text(selected.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!all) {
                    val total = agenda.scheduled.size + agenda.completed.size
                    Text("${if (selected == today) "今天" else "当日"} ${agenda.completed.size}/$total", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedValue = selected.minusWeeks(1).toString() }) { Text("上一周") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selectedValue = today.toString() }) { Text("回到今天") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selectedValue = selected.plusWeeks(1).toString() }) { Text("下一周") }
                    }
                    // Horizontally scroll at narrow widths/large font scales; never shrink hit targets.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cell = maxOf(48.dp, maxWidth / 7)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                            DateTimeUtil.week(selected).forEach { date ->
                                val count = state.todos.count { it.status == 0 && it.calendarDate(zone) == date }
                                val active = date == selected
                                Column(Modifier.width(cell).heightIn(min = 96.dp)
                                    .border(if (active) 1.dp else 0.dp, if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(28.dp))
                                    .selectableDate(active, date, count) { selectedValue = date.toString() }.padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1], style = MaterialTheme.typography.labelMedium)
                                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleMedium)
                                    Text(if (count > 0) "•" else " ", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else OutlinedTextField(query, { query = it }, label = { Text("搜索标题、备注、地点") }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), singleLine = true)
                HorizontalDivider()
            }
            if (state.loading) item { Text("正在读取待办…", Modifier.padding(vertical = 24.dp)) }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (all) {
                val filtered = state.todos.filter { listOfNotNull(it.title, it.note, it.location).any { text -> text.contains(query, ignoreCase = true) } }
                    .sortedWith(compareBy<TodoEntity> { it.status }.thenBy { it.dueAt == null }.thenBy { it.dueAt })
                if (!state.loading && filtered.isEmpty()) item { EmptyAgenda() }
                items(filtered, key = { it.id }) { todo -> TodoRow(todo, zone, onDetail, onComplete, Modifier.animateItem()) }
            } else {
                fun section(label: String, todos: List<TodoEntity>) {
                    if (todos.isNotEmpty()) {
                        item(key = label) { Text(label, style = MaterialTheme.typography.titleSmall, color = if (label == "已逾期") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) }
                        items(todos, key = { it.id }) { todo -> TodoRow(todo, zone, onDetail, onComplete, Modifier.animateItem()) }
                    }
                }
                section("已逾期", agenda.overdue)
                section(if (selected == today) "今天" else "当日日程", agenda.scheduled)
                section("无时间", agenda.undated)
                if (agenda.completed.isNotEmpty()) {
                    item { TextButton(onClick = { showCompleted = !showCompleted }) { Text("${if (showCompleted) "收起" else "展开"}已完成 · ${agenda.completed.size}") } }
                    if (showCompleted) items(agenda.completed, key = { it.id }) { TodoRow(it, zone, onDetail, onComplete, Modifier.animateItem()) }
                }
                if (!state.loading && state.error == null && agenda.empty) item { EmptyAgenda() }
            }
        }
    }
}

private fun Modifier.selectableDate(active: Boolean, date: LocalDate, count: Int, click: () -> Unit) =
    this.semantics { selected = active; contentDescription = "$date，$count 项待办" }.clickable(onClick = click)

@Composable
fun TodoRow(todo: TodoEntity, zone: ZoneId, onDetail: (Long) -> Unit, onComplete: (TodoEntity) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clickable { onDetail(todo.id) }) {
        Row(Modifier.heightIn(min = 76.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = todo.status == 1, onCheckedChange = { onComplete(todo) }, modifier = Modifier.semantics { contentDescription = if (todo.status == 1) "重新打开 ${todo.title}" else "完成 ${todo.title}" })
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(todo.title, style = MaterialTheme.typography.bodyLarge, textDecoration = if (todo.status == 1) TextDecoration.LineThrough else null)
                val time = if (todo.allDay) "${todo.allDayDate} · 全天" else todo.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm")) } ?: "无时间"
                Text(listOfNotNull(time, todo.location?.takeIf(String::isNotBlank), if (todo.repeatRule != null) "重复" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(thickness = 0.5.dp)
    }
}

@Composable
private fun EmptyAgenda() {
    val color = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(80.dp)) {
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .1f), size = androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .48f), androidx.compose.ui.geometry.Offset(size.width * .46f, size.height * .6f), 1.5.dp.toPx())
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .46f, size.height * .6f), androidx.compose.ui.geometry.Offset(size.width * .68f, size.height * .35f), 1.5.dp.toPx())
        }
        Text("给今天留一点从容", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text("点击 +，记下下一件事", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
