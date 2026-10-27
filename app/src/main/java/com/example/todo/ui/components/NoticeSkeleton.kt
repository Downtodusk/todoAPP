package com.example.todo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.todo.ui.theme.LocalReducedMotion

/** Reused by local loading and the M4 import preview; motion reduction keeps it static. */
@Composable
fun NoticeSkeleton(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val shift = if (reduced) 0f else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(-400f, 1200f,
            infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
        value
    }
    val base = MaterialTheme.colorScheme.surfaceVariant
    val brush = Brush.linearGradient(listOf(base, MaterialTheme.colorScheme.surface, base),
        Offset(shift, 0f), Offset(shift + 400f, 250f))
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp).semantics {
        contentDescription = "正在读取内容"; liveRegion = LiveRegionMode.Polite
    }, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(.75f, 1f, .55f).forEach { fraction ->
            Spacer(Modifier.fillMaxWidth(fraction).height(20.dp).background(brush, RoundedCornerShape(8.dp)))
        }
    }
}
