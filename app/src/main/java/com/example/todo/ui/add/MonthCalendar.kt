package com.example.todo.ui.add

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.todo.ui.components.LineIcons
import java.time.*

/** Monday-first, calendar dates only: no conversion through the device time zone. */
internal fun monthCells(month: YearMonth): List<LocalDate> {
    val start = month.atDay(1).minusDays(month.atDay(1).dayOfWeek.value - 1L)
    val weeks = (month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
    return List(weeks * 7) { start.plusDays(it.toLong()) }
}

@Composable
fun MonthCalendar(selected: LocalDate?, enabled: Boolean, markedDates: Set<LocalDate> = emptySet(), onMonthClick: () -> Unit = {}, onClear: () -> Unit = {}, onSelect: (LocalDate) -> Unit) {
    val layoutScale = maxOf(1f, LocalDensity.current.fontScale / 1.3f)
    val today = LocalDate.now()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected ?: today).toString()) }
    val month = YearMonth.parse(monthText)
    LaunchedEffect(selected) { selected?.let { monthText = YearMonth.from(it).toString() } }
    Column(Modifier.fillMaxWidth().testTag("editor-calendar")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthText = month.minusMonths(1).toString() }, enabled = enabled) { Icon(LineIcons.Back, "上个月", Modifier.size(18.dp)) }
            Text("${month.year}年 ${month.monthValue}月", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).clip(CircleShape).clickable(enabled = enabled, onClick = onMonthClick).padding(vertical = 12.dp).semantics { contentDescription = "选择年月" }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = enabled) { Icon(LineIcons.Forward, "下个月", Modifier.size(18.dp)) }
        }
        BoxWithConstraints {
            val cellWidth = maxOf(48.dp, (40 * layoutScale).dp, maxWidth / 7)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row { listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                    Box(Modifier.width(cellWidth).height(32.dp), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } }
                monthCells(month).chunked(7).forEach { week -> Row {
                    week.forEach { day ->
                        val active = selected == day
                        val inMonth = YearMonth.from(day) == month
                        Box(Modifier.width(cellWidth).height(maxOf(48.dp, (48 * layoutScale).dp)).clip(CircleShape)
                            .semantics { this.selected = active; contentDescription = "选择日期 $day" }
                            .clickable(enabled = enabled) { onSelect(day) }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size((38 * layoutScale).dp).background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .border(1.dp, if (day == today && !active) MaterialTheme.colorScheme.outlineVariant else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${day.dayOfMonth}", style = MaterialTheme.typography.bodyLarge,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (day in markedDates && !active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(3.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    }
                } }
            }
        }
        if (selected != null) TextButton(onClick = onClear, enabled = enabled, modifier = Modifier.align(Alignment.End)) { Text("清除日期") }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = .5.dp)
    }
}
