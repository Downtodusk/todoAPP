package com.example.todo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.example.todo.data.settings.UserSettings

@Composable
fun TodoTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFEDB29C),
            background = androidx.compose.ui.graphics.Color(0xFF201E1B), surface = androidx.compose.ui.graphics.Color(0xFF201E1B))
        else -> lightColorScheme(primary = Terracotta, background = WarmWhite, surface = WarmWhite,
            onBackground = Charcoal, onSurface = Charcoal, outlineVariant = WarmLine)
    }
    val base = Typography()
    MaterialTheme(colorScheme = colors, typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
    ), content = content)
}
