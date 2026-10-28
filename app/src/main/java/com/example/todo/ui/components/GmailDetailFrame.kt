package com.example.todo.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import com.example.todo.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Geometry is evaluated by the render layer, without remeasuring the calendar/text on drag. */
internal object DetailBackGeometry {
    const val MinScale = .90f
    fun scale(progress: Float) = 1f - (1f - MinScale) * progress.coerceIn(0f, 1f)
    fun radius(progress: Float) = 28f * progress.coerceIn(0f, 1f)
    fun horizontal(progress: Float, edge: Int) = 12f * progress.coerceIn(0f, 1f) * if (edge == BackEventCompat.EDGE_LEFT) 1 else -1
}

private class RetractingShape(private val radius: Float, private val collapse: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val inset = size.height * .5f * collapse.coerceIn(0f, 1f)
        return Outline.Rounded(RoundRect(0f, inset, size.width, size.height - inset,
            CornerRadius(radius.coerceAtMost((size.height - 2f * inset).coerceAtLeast(0f) / 2f))))
    }
}

@Composable
fun GmailDetailFrame(enabled: Boolean, preview: ImageBitmap?, onBack: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit) {
    if (!enabled) { content(onBack); return }
    val scope = rememberCoroutineScope()
    val latestBack by rememberUpdatedState(onBack)
    val progress = remember { Animatable(0f) }
    val collapse = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var closing by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current.density
    // Shared by toolbar, save/delete and system Back. Pop only after the last visual frame.
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            recovery?.cancel()
            scope.launch {
                if (progress.value < .35f) progress.animateTo(.35f, tween(80, easing = Motion.Enter))
                collapse.animateTo(1f, tween(200, easing = Motion.Enter))
                latestBack()
            }
        }
    }
    PredictiveBackHandler(enabled = !closing) { events ->
        recovery?.cancel()
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            close()
        } catch (_: CancellationException) {
            // A cancelled gesture must keep the draft, scroll and destination intact.
            recovery = scope.launch { progress.animateTo(0f, tween(180, easing = Motion.Enter)) }
        }
    }
    BackHandler(enabled = closing) { /* Ignore repeated Back while the accepted pop finishes. */ }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            preview?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt())) }
            drawRect(Color.Black, alpha = .12f * progress.value * (1f - collapse.value))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            scaleX = DetailBackGeometry.scale(p); scaleY = scaleX
            translationX = DetailBackGeometry.horizontal(p, edge) * density * (1f - collapse.value)
            shape = RetractingShape(DetailBackGeometry.radius(p) * density, collapse.value)
            clip = true
            // Keep text opaque until the final sliver, avoiding two overlapping calendars/titles.
            alpha = (1f - (collapse.value - .8f) / .2f).coerceIn(0f, 1f)
        }.testTag("detail-motion-frame")) { content(close) }
    }
}
