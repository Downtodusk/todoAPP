package com.example.todo.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.staticCompositionLocalOf

object Motion {
    const val DurationShort = 150
    const val DurationMedium = 300
    const val DurationCheck = 200
    val Enter = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    fun <T> interaction(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
}

val LocalReducedMotion = staticCompositionLocalOf { false }
