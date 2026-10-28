package com.example.todo.ui.today

import com.example.todo.R
import com.example.todo.i18n.t
import com.example.todo.i18n.appLocale
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.todo.data.local.TodoEntity
import com.example.todo.ui.components.*
import com.example.todo.ui.theme.*
import com.example.todo.domain.calendarDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class RowAction { Complete, Closed, Delete }

@Composable
fun TodoRow(todo: TodoEntity, zone: ZoneId, onDetail: (Long) -> Unit,
    onComplete: (TodoEntity) -> Job, onDelete: (Long) -> Job, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val timeColumnWidth = maxOf(60.dp, (48 * LocalDensity.current.fontScale).dp)
    val actionWidth = 88.dp
    val actionPx = with(LocalDensity.current) { actionWidth.toPx() }
    val swipe = remember { AnchoredDraggableState(RowAction.Closed) }
    SideEffect { swipe.updateAnchors(DraggableAnchors {
        RowAction.Complete at -actionPx
        RowAction.Closed at 0f
        RowAction.Delete at actionPx
    }) }
    val fling = AnchoredDraggableDefaults.flingBehavior(swipe, positionalThreshold = { it * .4f }, animationSpec = Motion.interaction())
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) .97f else 1f, Motion.interaction(), label = "row press")
    val check = remember { Animatable(if (todo.status == 1) 1f else 0f) }
    val strike = remember { Animatable(if (todo.status == 1) 1f else 0f) }
    val disappear = remember { Animatable(1f) }
    var busy by remember { mutableStateOf(false) }
    val latestStatus by rememberUpdatedState(todo.status)
    var menu by remember { mutableStateOf(false) }
    var titleLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(todo.revision, todo.status) {
        check.snapTo(if (todo.status == 1) 1f else 0f)
        strike.snapTo(if (todo.status == 1) 1f else 0f)
        disappear.snapTo(1f)
        swipe.snapTo(RowAction.Closed)
    }
    LaunchedEffect(swipe) {
        snapshotFlow { swipe.targetValue }.collect { target ->
            if (target != RowAction.Closed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    fun complete() {
        if (busy) return
        busy = true
        scope.launch {
            var submitted = false
            try {
                if (todo.status == 0) {
                    if (!reduced) {
                        check.animateTo(1f, tween(Motion.DurationCheck, easing = Motion.Enter))
                        strike.animateTo(1f, tween(Motion.DurationShort, easing = Motion.Enter))
                    }
                    disappear.animateTo(0f, tween(Motion.DurationShort))
                }
                // Mutate only after the gesture/feedback animation has finished.
                submitted = true
                onComplete(todo).join()
            } finally {
                // Leaving the page may cancel feedback, but must not discard an accepted completion.
                if (!submitted) onComplete(todo)
                busy = false
                // If an operation failed, leave the unchanged row available for retry.
                check.snapTo(if (latestStatus == 1) 1f else 0f)
                strike.snapTo(if (latestStatus == 1) 1f else 0f)
                disappear.snapTo(1f)
            }
        }
    }
    val offset = swipe.offset.takeIf { it.isFinite() } ?: 0f
    Box(modifier.testTag("todo-row-${todo.id}").clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * if (reduced) 1f else disappear.value).roundToInt()) { placeable.placeRelative(0, 0) }
    }) {
        if (abs(offset) > 1f) {
            val delete = offset > 0f
            val label = if (delete) t(R.string.delete) else if (todo.status == 1) t(R.string.reopen) else t(R.string.complete)
            val background = if (delete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            val ink = if (delete) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
            Box(Modifier.matchParentSize(), contentAlignment = if (delete) Alignment.CenterStart else Alignment.CenterEnd) {
                Column(Modifier.fillMaxHeight().width(actionWidth).background(background)
                    .semantics { contentDescription = t(R.string.confirm_task_action, label, todo.title) }
                    .clickable(enabled = !busy) {
                        if (delete) {
                            busy = true
                            scope.launch { try { onDelete(todo.id).join() } finally { busy = false; swipe.snapTo(RowAction.Closed) } }
                        } else { scope.launch { swipe.animateTo(RowAction.Closed) }; complete() }
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(if (delete) LineIcons.Delete else LineIcons.Check, null, tint = ink)
                    Text(label, color = ink, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(Modifier.fillMaxWidth().offset { IntOffset((swipe.offset.takeIf { it.isFinite() } ?: 0f).roundToInt(), 0) }
            .anchoredDraggable(swipe, Orientation.Horizontal, enabled = !busy, flingBehavior = fling)
            .background(MaterialTheme.colorScheme.surface)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = disappear.value }
            .clickable(enabled = !busy, interactionSource = interactions, indication = ripple()) { if (abs(offset) > 1f) scope.launch { swipe.animateTo(RowAction.Closed) } else onDetail(todo.id) }
            .semantics { customActions = listOf(
                CustomAccessibilityAction(t(R.string.delete)) { if (!busy) onDelete(todo.id); true },
                CustomAccessibilityAction(if (todo.status == 1) t(R.string.reopen) else t(R.string.complete)) { complete(); true }) }) {
            Row(Modifier.heightIn(min = 76.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val completed = todo.status == 1
                val dated = todo.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
                if (dated != null && !todo.allDay && !completed) Column(Modifier.width(timeColumnWidth), horizontalAlignment = Alignment.Start) {
                    Text(dated.format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = LocalEditorialFont.current, fontSize = 18.sp, lineHeight = 24.sp), color = MaterialTheme.colorScheme.onSurface)
                    if (todo.calendarDate(zone) != LocalDate.now(zone)) Text(dated.format(DateTimeFormatter.ofPattern("M/d")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else if (!completed) Spacer(Modifier.width(timeColumnWidth))
                val outline = MaterialTheme.colorScheme.outline
                val checkedColor = MaterialTheme.colorScheme.onSurfaceVariant
                val checkInk = MaterialTheme.colorScheme.surface
                Canvas(Modifier.size(48.dp).semantics {
                    contentDescription = if (todo.status == 1) t(R.string.reopen_task, todo.title) else t(R.string.complete_task, todo.title)
                }.toggleable(todo.status == 1, enabled = !busy, role = Role.Checkbox, onValueChange = { complete() }).padding(13.dp)) {
                    drawCircle(outline, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                    drawCircle(checkedColor.copy(alpha = check.value), radius = size.minDimension / 2 - 1.dp.toPx())
                    val path = Path().apply { moveTo(size.width * .2f, size.height * .5f); lineTo(size.width * .43f, size.height * .73f); lineTo(size.width * .83f, size.height * .27f) }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val segment = Path(); measure.getSegment(0f, measure.length * check.value, segment, true)
                    drawPath(segment, checkInk, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp, end = 2.dp)) {
                    val textColor = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    Text(todo.title, style = MaterialTheme.typography.bodyLarge, color = textColor,
                        onTextLayout = { titleLayout = it }, modifier = Modifier.drawWithContent {
                            drawContent()
                            titleLayout?.let { layout ->
                                for (line in 0 until layout.lineCount) {
                                    val left = layout.getLineLeft(line); val right = layout.getLineRight(line)
                                    val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
                                    drawLine(textColor, Offset(left, y), Offset(left + (right - left) * strike.value, y), 1.dp.toPx())
                                }
                            }
                        })
                    val dateLabel = when {
                        completed || todo.calendarDate(zone)?.let { it != LocalDate.now(zone) } == true ->
                            if (todo.allDay) t(R.string.all_day_date, todo.allDayDate.orEmpty()) else dated?.format(DateTimeFormatter.ofPattern(t(R.string.mmm_d_hh_mm), appLocale()))
                        todo.dueAt == null -> t(R.string.unscheduled)
                        else -> null
                    }
                    val detail = listOfNotNull(dateLabel, todo.location?.takeIf(String::isNotBlank), if (todo.repeatRule != null) t(R.string.repeat) else null).joinToString(" · ")
                    if (detail.isNotEmpty()) Text(detail, modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) { Icon(LineIcons.More, t(R.string.more_task_actions, todo.title), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(t(R.string.view_details)) }, onClick = { menu = false; onDetail(todo.id) })
                        DropdownMenuItem(text = { Text(if (completed) t(R.string.reopen) else t(R.string.complete)) }, onClick = { menu = false; complete() })
                        DropdownMenuItem(text = { Text(t(R.string.delete)) }, onClick = { menu = false; onDelete(todo.id) })
                    }
                }
            }
            HorizontalDivider(thickness = .5.dp)
        }
    }
}
