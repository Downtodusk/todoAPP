package com.example.todo.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import com.example.todo.ui.theme.LocalReducedMotion
import com.example.todo.ui.theme.Motion

val LocalTodoSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalTodoVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalSharedTodoId = staticCompositionLocalOf<Long?> { null }

@Composable
fun DetailSharedRoot(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) SharedTransitionLayout {
        CompositionLocalProvider(LocalTodoSharedScope provides this) { content() }
    } else content()
}

/** Keep the 0.4.0 container trajectory, but measure the expensive editor at its final size. */
@Composable
fun Modifier.todoSharedBounds(id: Long): Modifier {
    val shared = LocalTodoSharedScope.current
    val visibility = LocalTodoVisibilityScope.current
    if (LocalReducedMotion.current || shared == null || visibility == null || LocalSharedTodoId.current != id) return this
    return with(shared) {
        sharedBounds(rememberSharedContentState("todo/$id/container"),
            animatedVisibilityScope = visibility,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = { _, _ -> tween(Motion.DurationMedium, easing = Motion.Enter) })
            .clipToBounds()
            .skipToLookaheadSize()
    }
}
