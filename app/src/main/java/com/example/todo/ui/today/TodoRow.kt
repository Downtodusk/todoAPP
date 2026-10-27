package com.example.todo.ui.today

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TodoRow(todo: TodoEntity, zone: ZoneId, onDetail: (Long) -> Unit,
    onComplete: (TodoEntity) -> Job, onDelete: (Long) -> Job, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val swipe = rememberSwipeToDismissBoxState(positionalThreshold = { it * .35f })
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && !reduced) .97f else 1f, Motion.interaction(), label = "row press")
    val check = remember { Animatable(if (todo.status == 1) 1f else 0f) }
    val strike = remember { Animatable(if (todo.status == 1) 1f else 0f) }
    val disappear = remember { Animatable(1f) }
    var busy by remember { mutableStateOf(false) }
    val latestStatus by rememberUpdatedState(todo.status)
    var titleLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(todo.revision, todo.status) {
        check.snapTo(if (todo.status == 1) 1f else 0f)
        strike.snapTo(if (todo.status == 1) 1f else 0f)
        disappear.snapTo(1f)
        swipe.snapTo(SwipeToDismissBoxValue.Settled)
    }
    LaunchedEffect(swipe) {
        snapshotFlow { swipe.targetValue }.collect { target ->
            if (target != SwipeToDismissBoxValue.Settled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
    SwipeToDismissBox(state = swipe, modifier = modifier.testTag("todo-row-${todo.id}").clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * if (reduced) 1f else disappear.value).roundToInt()) { placeable.placeRelative(0, 0) }
    }, gesturesEnabled = !busy,
        enableDismissFromEndToStart = todo.status == 0,
        onDismiss = { direction ->
            if (!busy) {
                busy = true
                scope.launch {
                    try {
                        if (direction == SwipeToDismissBoxValue.StartToEnd) onDelete(todo.id).join()
                        else if (direction == SwipeToDismissBoxValue.EndToStart) onComplete(todo).join()
                    } finally { busy = false; swipe.snapTo(SwipeToDismissBoxValue.Settled) }
                }
            }
        },
        backgroundContent = {
            val delete = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (delete) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            val alpha = runCatching { abs(swipe.requireOffset()) / 200f }.getOrDefault(0f).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().background(color.copy(alpha = alpha)).padding(horizontal = 20.dp),
                contentAlignment = if (delete) Alignment.CenterStart else Alignment.CenterEnd) {
                Row(Modifier.graphicsLayer { this.alpha = alpha }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tint = if (delete) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    Icon(if (delete) LineIcons.Delete else LineIcons.Check, null, tint = tint)
                    Text(if (delete) "删除" else "完成", color = tint)
                }
            }
        }) {
        Column(Modifier.fillMaxWidth().todoSharedBounds(todo.id).background(MaterialTheme.colorScheme.surface)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = disappear.value }
            .clickable(enabled = !busy, interactionSource = interactions, indication = ripple()) { onDetail(todo.id) }
            .semantics { customActions = listOf(
                CustomAccessibilityAction("删除") { if (!busy) onDelete(todo.id); true },
                CustomAccessibilityAction(if (todo.status == 1) "重新打开" else "完成") { complete(); true }) }) {
            Row(Modifier.heightIn(min = 76.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val outline = MaterialTheme.colorScheme.primary
                Canvas(Modifier.size(48.dp).semantics {
                    contentDescription = if (todo.status == 1) "重新打开 ${todo.title}" else "完成 ${todo.title}"
                }.toggleable(todo.status == 1, enabled = !busy, role = Role.Checkbox, onValueChange = { complete() }).padding(14.dp)) {
                    drawRoundRect(outline, cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    val path = Path().apply { moveTo(size.width * .2f, size.height * .5f); lineTo(size.width * .43f, size.height * .73f); lineTo(size.width * .83f, size.height * .27f) }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val segment = Path(); measure.getSegment(0f, measure.length * check.value, segment, true)
                    drawPath(segment, outline, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    val textColor = MaterialTheme.colorScheme.onSurface
                    Text(todo.title, style = MaterialTheme.typography.bodyLarge,
                        onTextLayout = { titleLayout = it }, modifier = Modifier.todoSharedBounds(todo.id, title = true).drawWithContent {
                            drawContent()
                            titleLayout?.let { layout ->
                                for (line in 0 until layout.lineCount) {
                                    val left = layout.getLineLeft(line); val right = layout.getLineRight(line)
                                    val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
                                    drawLine(textColor, Offset(left, y), Offset(left + (right - left) * strike.value, y), 1.dp.toPx())
                                }
                            }
                        })
                    val time = if (todo.allDay) "${todo.allDayDate} · 全天" else todo.dueAt?.let {
                        Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                    } ?: "无时间"
                    Text(listOfNotNull(time, todo.location?.takeIf(String::isNotBlank), if (todo.repeatRule != null) "重复" else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(thickness = .5.dp)
        }
    }
}
