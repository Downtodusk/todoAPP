package com.example.todo.ui.today

import com.example.todo.R
import com.example.todo.i18n.t
import com.example.todo.i18n.appLocale
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.Job
import com.example.todo.ui.components.NoticeSkeleton
import com.example.todo.ui.theme.LocalReducedMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.example.todo.data.local.TodoEntity
import com.example.todo.domain.*
import com.example.todo.ui.components.LineIcons
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(state: TodoListState, today: LocalDate, zone: ZoneId, all: Boolean,
    onDetail: (Long) -> Unit, onComplete: (TodoEntity) -> Job, onDelete: (Long) -> Job, onAdd: () -> Unit,
    onAll: () -> Unit, onSettings: () -> Unit, onBack: () -> Unit, adding: Boolean = false) {
    val pagePadding = if (LocalConfiguration.current.screenWidthDp < 384) 12.dp else 24.dp
    val reduced = LocalReducedMotion.current
    var selectedValue by rememberSaveable { mutableStateOf(today.toString()) }
    var lastToday by rememberSaveable { mutableStateOf(today.toString()) }
    LaunchedEffect(today) { if (selectedValue == lastToday) selectedValue = today.toString(); lastToday = today.toString() }
    val selected = LocalDate.parse(selectedValue)
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val agenda = remember(state.todos, selected, today, zone) { partitionAgenda(state.todos, selected, today, zone) }
    Scaffold(
        floatingActionButton = { AnimatedVisibility(!adding, enter = fadeIn(), exit = fadeOut()) { FloatingActionButton(onClick = onAdd, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(56.dp)) { Icon(LineIcons.Add, t(R.string.add_task)) } } },
        topBar = { Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (all) onBack else onSettings) { Icon(if (all) LineIcons.Back else LineIcons.Menu, if (all) t(R.string.back) else t(R.string.settings), modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.weight(1f))
            if (all) IconButton(onClick = onSettings) { Icon(LineIcons.Settings, t(R.string.settings)) }
            else TextButton(onClick = onAll, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(t(R.string.all_tasks), style = MaterialTheme.typography.bodyMedium) }
        } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = pagePadding, end = pagePadding, bottom = 100.dp)) {
            item {
                Text(if (all) t(R.string.all_tasks) else if (selected == today) t(R.string.today) else t(R.string.schedule), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 14.dp))
                Text(selected.format(DateTimeFormatter.ofPattern(t(R.string.mmm_d_eeee), appLocale())),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                if (!all) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selected != today) TextButton(onClick = { selectedValue = today.toString() }, contentPadding = PaddingValues(0.dp)) { Text(t(R.string.back_to_today)) }
                        else Text(t(R.string.completed_count, agenda.completed.size, agenda.scheduled.size + agenda.completed.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { selectedValue = selected.minusWeeks(1).toString() }) { Icon(LineIcons.Back, t(R.string.previous_week), Modifier.size(18.dp)) }
                        IconButton(onClick = { selectedValue = selected.plusWeeks(1).toString() }) { Icon(LineIcons.Forward, t(R.string.next_week), Modifier.size(18.dp)) }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cell = maxOf(48.dp, maxWidth / 7)
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 22.dp)) {
                            DateTimeUtil.week(selected).forEach { date ->
                                val count = state.todos.count { it.status == 0 && it.calendarDate(zone) == date }
                                val active = date == selected
                                Column(Modifier.width(cell).heightIn(min = 88.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .border(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(28.dp))
                                    .selectableDate(active, date, count) { selectedValue = date.toString() }.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(listOf(t(R.string.mon), t(R.string.tue), t(R.string.wed), t(R.string.thu), t(R.string.fri), t(R.string.sat), t(R.string.sun))[date.dayOfWeek.value - 1], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge)
                                    val dotColor = MaterialTheme.colorScheme.primary
                                    Canvas(Modifier.size(width = 18.dp, height = 5.dp)) {
                                        repeat(minOf(count, 3)) { index -> drawCircle(dotColor, 1.7.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2 + (index - (minOf(count, 3) - 1) / 2f) * 6.dp.toPx(), size.height / 2)) }
                                    }
                                }
                            }
                        }
                    }
                } else OutlinedTextField(query, { query = it }, label = { Text(t(R.string.search_titles_notes_and_locations)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), singleLine = true)
                HorizontalDivider()
            }
            if (state.loading) item { NoticeSkeleton() }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (all) {
                val filtered = state.todos.filter { listOfNotNull(it.title, it.note, it.location).any { text -> text.contains(query, ignoreCase = true) } }
                    .sortedWith(compareBy<TodoEntity> { it.status }.thenBy { it.dueAt == null }.thenBy { it.dueAt })
                if (!state.loading && filtered.isEmpty()) item { EmptyAgenda() }
                items(filtered, key = { it.id }) { todo -> TodoRow(todo, zone, onDetail, onComplete, onDelete, Modifier.animateItem(placementSpec = if (reduced) null else com.example.todo.ui.theme.Motion.interaction())) }
            } else {
                fun section(label: String, todos: List<TodoEntity>) {
                    if (todos.isNotEmpty()) {
                        item(key = label) { Text(label, style = MaterialTheme.typography.titleSmall, color = if (label == t(R.string.overdue)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) }
                        items(todos, key = { it.id }) { todo -> TodoRow(todo, zone, onDetail, onComplete, onDelete, Modifier.animateItem(placementSpec = if (reduced) null else com.example.todo.ui.theme.Motion.interaction())) }
                    }
                }
                section(t(R.string.overdue), agenda.overdue)
                section(t(R.string.all_day), agenda.scheduled.filter { it.allDay })
                section(t(R.string.schedule), agenda.scheduled.filter { !it.allDay })
                section(t(R.string.unscheduled), agenda.undated)
                if (agenda.completed.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 52.dp).clickable { showCompleted = !showCompleted }
                        .semantics { contentDescription = t(if (showCompleted) R.string.collapse_completed else R.string.expand_completed, agenda.completed.size) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(LineIcons.Down, null, Modifier.size(18.dp).rotate(if (showCompleted) 180f else 0f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t(R.string.completed), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        Text("${agenda.completed.size}", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    if (showCompleted) items(agenda.completed, key = { it.id }) { TodoRow(it, zone, onDetail, onComplete, onDelete, Modifier.animateItem(placementSpec = if (reduced) null else com.example.todo.ui.theme.Motion.interaction())) }
                }
                if (!state.loading && state.error == null && agenda.empty) item { EmptyAgenda() }
            }
        }
    }
}

private fun Modifier.selectableDate(active: Boolean, date: LocalDate, count: Int, click: () -> Unit) =
    this.semantics { selected = active; contentDescription = t(R.string.date_task_count, date, count) }.clickable(onClick = click)

@Composable
private fun EmptyAgenda() {
    val color = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(80.dp)) {
            drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .1f), size = androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .8f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .35f, size.height * .48f), androidx.compose.ui.geometry.Offset(size.width * .46f, size.height * .6f), 1.5.dp.toPx())
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .46f, size.height * .6f), androidx.compose.ui.geometry.Offset(size.width * .68f, size.height * .35f), 1.5.dp.toPx())
        }
        Text(t(R.string.make_room_for_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(t(R.string.tap_to_add_your_next_task), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
