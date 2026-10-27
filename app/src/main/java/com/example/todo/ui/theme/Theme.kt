package com.example.todo.ui.theme

import android.os.Build
import android.app.ActivityManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.todo.data.settings.UserSettings

@Composable
fun TodoTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val reduced = settings.reduceMotion ?: LocalContext.current.getSystemService(ActivityManager::class.java).isLowRamDevice
    val dark = when (settings.theme) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
    ApplySystemBarAppearance(dark)
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme(primary = Color(0xFFEDB29C), onPrimary = Color(0xFF4B2619),
            background = Color(0xFF211F1C), surface = Color(0xFF211F1C), onBackground = Color(0xFFE9E2D8), onSurface = Color(0xFFE9E2D8),
            onSurfaceVariant = Color(0xFFB9B0A5), outline = Color(0xFF91867A), outlineVariant = Color(0xFF49423A),
            primaryContainer = Color(0xFF513125), onPrimaryContainer = Color(0xFFFFDCCD), secondary = Color(0xFFCEB7A5),
            onSecondary = Color(0xFF3C2E23), secondaryContainer = Color(0xFF514336), onSecondaryContainer = Color(0xFFF0DDCB),
            surfaceVariant = Color(0xFF49423A),
            surfaceContainer = Color(0xFF2B2722), surfaceContainerLow = Color(0xFF27231F), surfaceContainerHigh = Color(0xFF342E28))
        else -> lightColorScheme(primary = Terracotta, background = WarmWhite, surface = WarmWhite,
            onPrimary = Color.White, primaryContainer = Color(0xFFF0DDCF), onPrimaryContainer = Color(0xFF63351F),
            secondary = Color(0xFF796654), onSecondary = Color.White, secondaryContainer = Color(0xFFEDE5D8), onSecondaryContainer = Charcoal,
            onBackground = Charcoal, onSurface = Charcoal, onSurfaceVariant = Color(0xFF746E65),
            outline = Color(0xFF8E8579), outlineVariant = WarmLine, surfaceVariant = Color(0xFFF1EDE3),
            surfaceContainerLowest = WarmWhite, surfaceContainerLow = Color(0xFFF7F3EB), surfaceContainer = Color(0xFFF3EEE4),
            surfaceContainerHigh = Color(0xFFEFE8DD), surfaceContainerHighest = Color(0xFFE9E1D4))
    }
    val fonts = AppFonts.get(LocalContext.current.resources)
    val defaults = Typography()
    val base = defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = fonts.serif), displayMedium = defaults.displayMedium.copy(fontFamily = fonts.serif), displaySmall = defaults.displaySmall.copy(fontFamily = fonts.serif),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = fonts.serif), bodyMedium = defaults.bodyMedium.copy(fontFamily = fonts.sans), bodySmall = defaults.bodySmall.copy(fontFamily = fonts.sans),
        titleSmall = defaults.titleSmall.copy(fontFamily = fonts.sans), labelLarge = defaults.labelLarge.copy(fontFamily = fonts.sans), labelMedium = defaults.labelMedium.copy(fontFamily = fonts.sans), labelSmall = defaults.labelSmall.copy(fontFamily = fonts.sans))
    CompositionLocalProvider(LocalReducedMotion provides reduced, LocalEditorialFont provides fonts.serif) {
    MaterialTheme(colorScheme = colors, typography = base.copy(
        headlineLarge = base.headlineLarge.copy(fontFamily = fonts.serif, fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = fonts.serif, fontWeight = FontWeight.Normal),
        headlineSmall = base.headlineSmall.copy(fontFamily = fonts.serif, fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontFamily = fonts.serif, fontSize = 20.sp, fontWeight = FontWeight.Normal),
        titleMedium = base.titleMedium.copy(fontFamily = fonts.serif, fontSize = 18.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    ), content = content)
    }
}

/** Dialogs have a separate window and do not inherit the app's explicit theme override. */
@Composable
internal fun ApplySystemBarAppearance(dark: Boolean) {
    val view = LocalView.current
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    val window = (view.parent as? DialogWindowProvider)?.window ?: activity?.window
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= 29) it.isNavigationBarContrastEnforced = false
        }
    }
}
