package com.example.todo.ui.theme

import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface
import com.example.todo.R

internal data class AppFontFamilies(val serif: FontFamily, val sans: FontFamily)
val LocalEditorialFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

internal object AppFonts {
    @Volatile private var cached: AppFontFamilies? = null
    @Synchronized fun get(resources: Resources): AppFontFamilies {
        cached?.let { return it }
        val fonts = if (Build.VERSION.SDK_INT >= 29) modern(resources) else {
            // Android 8/9 cannot construct a public custom fallback chain. Keep bundled CJK
            // coverage and the font's own Source Sans-derived Latin on those versions.
            val compatible = FontFamily(Typeface(resources.getFont(R.font.source_han_sans_sc_regular)))
            AppFontFamilies(compatible, compatible)
        }
        cached = fonts
        return fonts
    }
    @RequiresApi(29)
    private fun modern(resources: Resources): AppFontFamilies {
        val chinese = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(resources, R.font.source_han_sans_sc_regular).build()).build()
        fun family(latin: Int): FontFamily {
            val primary = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(resources, latin).build()).build()
            val native = android.graphics.Typeface.CustomFallbackBuilder(primary).addCustomFallback(chinese)
                .setSystemFallback("sans-serif").build()
            return FontFamily(Typeface(native))
        }
        return AppFontFamilies(family(R.font.source_serif_4_regular), family(R.font.source_sans_3_regular))
    }
}
