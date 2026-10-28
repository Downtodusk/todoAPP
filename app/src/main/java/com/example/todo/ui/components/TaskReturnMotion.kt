package com.example.todo.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

internal data class ReturnAnchor(val layer: GraphicsLayer, var bounds: Rect = Rect.Zero, var fullyVisible: Boolean = false)
internal data class TaskReturnRequest(val image: ImageBitmap, val from: Rect, val todoId: Long?,
    val initialProgress: Float, val edge: Int, val onFinished: () -> Unit)

@Stable
class TaskReturnState {
    internal val anchors = mutableMapOf<Long, ReturnAnchor>()
    internal var request by mutableStateOf<TaskReturnRequest?>(null)
    var hiddenId by mutableStateOf<Long?>(null); private set
    internal var origin = Offset.Zero
    internal var viewport = Size.Zero
    // Exposed internally for deterministic instrumentation of geometry, not UI text.
    internal var drawnBounds: Rect? = null
    internal var targetBounds: Rect? = null
    internal var animationStarted = false
    internal var detailOpacity = 1f
    internal var rowOpacity = 0f

    fun cancel() { request = null; hiddenId = null }
    fun start(image: ImageBitmap, from: Rect, todoId: Long?, initialProgress: Float = 0f,
        edge: Int = -1, navigate: () -> Boolean, onFinished: () -> Unit = {}): Boolean {
        if (request != null) return false
        val next = TaskReturnRequest(image, from, todoId, initialProgress, edge, onFinished)
        animationStarted = false; targetBounds = null; drawnBounds = null; detailOpacity = 1f; rowOpacity = 0f
        hiddenId = todoId
        request = next
        if (!navigate()) { cancel(); return false }
        return true
    }
    internal fun complete(next: TaskReturnRequest) {
        if (request === next) { cancel(); next.onFinished() }
    }
}

internal val ReturnMotionStateKey = SemanticsPropertyKey<TaskReturnState>("TaskReturnMotionState")

val LocalTaskReturn = staticCompositionLocalOf<TaskReturnState?> { null }

/** Only the returning row is recorded. It keeps its real layout slot while the overlay is visible. */
@Composable
fun Modifier.taskReturnAnchor(id: Long): Modifier {
    val host = LocalTaskReturn.current ?: return this
    val layer = rememberGraphicsLayer()
    val anchor = remember(layer) { ReturnAnchor(layer) }
    DisposableEffect(host, id, anchor) {
        host.anchors[id] = anchor
        onDispose { if (host.anchors[id] === anchor) host.anchors.remove(id) }
    }
    return this.onGloballyPositioned { coordinates ->
        val actual = Rect(coordinates.positionInWindow(), coordinates.size.toSize())
        val clipped = coordinates.boundsInWindow()
        anchor.bounds = actual
        anchor.fullyVisible = actual.width > 0 && actual.height > 0 &&
            kotlin.math.abs(actual.width - clipped.width) < 1 && kotlin.math.abs(actual.height - clipped.height) < 1
    }.drawWithContent {
        if (host.hiddenId == id) layer.record { this@drawWithContent.drawContent() }
        else drawContent()
    }
}

internal object TaskReturnPhases {
    const val PreludeMs = com.example.todo.ui.theme.Motion.DurationDetailShrink
    const val MorphMs = com.example.todo.ui.theme.Motion.DurationDetailMorph
    const val Handoff = .55f
    fun remainingPrelude(progress: Float) = (PreludeMs * (1f - progress.coerceIn(0f, 1f))).roundToInt()
    fun detailAlpha(fraction: Float) = (1f - fraction / Handoff).coerceIn(0f, 1f)
    fun rowAlpha(fraction: Float) = ((fraction - Handoff) / (1f - Handoff)).coerceIn(0f, 1f)
    fun interpolate(start: Rect, end: Rect, fraction: Float) = androidx.compose.ui.geometry.lerp(start, end, fraction.coerceIn(0f, 1f))
}

/** Fixed-size bitmap layers: no editor remeasure, no bitmap readback during animated frames. */
@Composable
fun TaskReturnOverlay(state: TaskReturnState, modifier: Modifier = Modifier) {
    val request = state.request ?: return
    val shrink = remember(request) { Animatable(request.initialProgress.coerceIn(0f, 1f)) }
    val morph = remember(request) { Animatable(0f) }
    var target by remember(request) { mutableStateOf<Rect?>(null) }
    var rowImage by remember(request) { mutableStateOf<ImageBitmap?>(null) }
    val density = LocalDensity.current.density
    val paper = MaterialTheme.colorScheme.surface
    LaunchedEffect(request) {
        // The real list is now mounted (or unfrozen after quick-add). Let its placement and draw run.
        repeat(2) { withFrameNanos { } }
        val anchor = request.todoId?.let { state.anchors[it] }
        if (anchor != null && anchor.fullyVisible && anchor.layer.size.width > 0) {
            try {
                val captured = anchor.layer.toImageBitmap()
                val bounds = anchor.bounds.translate(-state.origin)
                if (bounds.left >= 0 && bounds.top >= 0 && bounds.right <= state.viewport.width + 1 && bounds.bottom <= state.viewport.height + 1) {
                    rowImage = captured; target = bounds
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Missing/recycled row: use the agreed center fallback. */ }
        }
        state.targetBounds = target
        // Always visibly shrink, including button Back and OEMs with no progress events.
        state.animationStarted = true
        val remaining = TaskReturnPhases.remainingPrelude(shrink.value)
        if (remaining > 0) shrink.animateTo(1f, tween(remaining, easing = com.example.todo.ui.theme.Motion.Enter))
        morph.animateTo(1f, tween(TaskReturnPhases.MorphMs, easing = androidx.compose.animation.core.LinearEasing))
        state.complete(request)
    }
    BackHandler { /* One accepted return; swallow repeated Back until the visual handoff. */ }
    Canvas(modifier.fillMaxSize().testTag("task-return-overlay").clearAndSetSemantics { this[ReturnMotionStateKey] = state }
        .pointerInput(request) { awaitPointerEventScope { while (true) { awaitPointerEvent().changes.forEach { it.consume() } } } }) {
        val original = request.from.translate(-state.origin)
        val p = shrink.value
        val scale = DetailBackGeometry.scale(p)
        val small = Rect(original.center.x - original.width * scale / 2 + DetailBackGeometry.horizontal(p, request.edge) * density,
            original.center.y - original.height * scale / 2,
            original.center.x + original.width * scale / 2 + DetailBackGeometry.horizontal(p, request.edge) * density,
            original.center.y + original.height * scale / 2)
        val end = target ?: Rect(size.width / 2, size.height / 2, size.width / 2, size.height / 2)
        val fraction = morph.value
        val eased = fraction * fraction * (3f - 2f * fraction)
        val rect = TaskReturnPhases.interpolate(small, end, eased)
        val radius = (28 * density * p * (1f - eased)).coerceAtMost(rect.height / 2).coerceAtLeast(0f)
        val detailAlpha = TaskReturnPhases.detailAlpha(fraction)
        val rowAlpha = TaskReturnPhases.rowAlpha(fraction)
        state.drawnBounds = rect; state.detailOpacity = detailAlpha; state.rowOpacity = rowAlpha
        drawRect(Color.Black, alpha = .10f * p * (1f - fraction))
        if (rect.width > 0 && rect.height > 0) {
            val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }
            clipPath(path) {
                drawRect(paper, rect.topLeft, rect.size)
                // Detail moves downward independently of the container's destination direction.
                if (detailAlpha > 0) drawImage(request.image,
                    dstOffset = IntOffset(rect.left.roundToInt(), (rect.top + 32 * density * (1f - detailAlpha)).roundToInt()),
                    dstSize = IntSize((original.width * scale).roundToInt().coerceAtLeast(1), (original.height * scale).roundToInt().coerceAtLeast(1)),
                    alpha = detailAlpha, filterQuality = FilterQuality.Low)
                val image = rowImage
                val destination = target
                if (image != null && destination != null && rowAlpha > 0) drawImage(image,
                    dstOffset = IntOffset(rect.left.roundToInt(), (rect.center.y - destination.height / 2).roundToInt()),
                    dstSize = IntSize(destination.width.roundToInt(), destination.height.roundToInt()),
                    alpha = rowAlpha, filterQuality = FilterQuality.Low)
            }
        }
    }
}
