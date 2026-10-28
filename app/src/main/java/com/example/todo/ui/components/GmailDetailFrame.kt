package com.example.todo.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.example.todo.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

/** Geometry is evaluated by the render layer, without remeasuring the calendar/text on drag. */
internal object DetailBackGeometry {
    const val MinScale = .90f
    fun gestureProgress(raw: Float): Float {
        // System progress is often small even for a normal swipe. Reach the bounded shrink
        // earlier, easing into its limit rather than requiring a near-full-width gesture.
        val fraction = (raw / .6f).coerceIn(0f, 1f)
        return fraction * (2f - fraction)
    }
    fun scale(progress: Float) = 1f - (1f - MinScale) * progress.coerceIn(0f, 1f)
    fun radius(progress: Float) = 28f * progress.coerceIn(0f, 1f)
    fun horizontal(progress: Float, edge: Int) = 12f * progress.coerceIn(0f, 1f) * when (edge) { BackEventCompat.EDGE_LEFT -> 1; BackEventCompat.EDGE_RIGHT -> -1; else -> 0 }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GmailDetailFrame(enabled: Boolean, preview: ImageBitmap?, targetId: () -> Long?, onBack: () -> Boolean,
    content: @Composable (close: () -> Unit) -> Unit) {
    if (!enabled) { content { onBack() }; return }
    val host = checkNotNull(LocalTaskReturn.current)
    val layer = rememberGraphicsLayer()
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val scope = rememberCoroutineScope()
    val latestBack by rememberUpdatedState(onBack)
    val progress = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var closing by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current.density
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    // Capture once, then let the root overlay own the animation across navigation disposal.
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            recovery?.cancel()
            scope.launch {
                try {
                    focus.clearFocus(); keyboard?.hide()
                    if (imeVisible) withTimeoutOrNull(700) { snapshotFlow { imeVisible }.first { !it } }
                    val image = layer.toImageBitmap()
                    host.start(image, bounds, targetId(), progress.value, if (progress.value == 0f) -1 else edge, navigate = latestBack)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { latestBack() }
            }
        }
    }
    PredictiveBackHandler(enabled = !closing) { events ->
        recovery?.cancel()
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(DetailBackGeometry.gestureProgress(event.progress))
            }
            close()
        } catch (_: CancellationException) {
            // A cancelled gesture must keep the draft, scroll and destination intact.
            recovery = scope.launch { progress.animateTo(0f, tween(Motion.DurationDetailRecovery, easing = Motion.Enter)) }
        }
    }
    BackHandler(enabled = closing) { /* Ignore repeated Back while the accepted pop finishes. */ }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            preview?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            drawRect(Color.Black, alpha = .10f * progress.value)
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = androidx.compose.ui.geometry.Rect(it.positionInWindow(), it.size.toSize()) }.graphicsLayer {
            val p = progress.value
            scaleX = DetailBackGeometry.scale(p); scaleY = scaleX
            translationX = DetailBackGeometry.horizontal(p, edge) * density
            shape = RoundedCornerShape(DetailBackGeometry.radius(p).dp)
            clip = true
        }.testTag("detail-motion-frame").drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) { content(close) }
    }
}
