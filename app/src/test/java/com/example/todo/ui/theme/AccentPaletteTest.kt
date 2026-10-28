package com.example.todo.ui.theme

import org.junit.Assert.*
import org.junit.Test

class AccentPaletteTest {
    @Test fun extremeCustomColorsRemainReadableInBothThemes() {
        val samples = listOf(0xFF000000, 0xFFFFFFFF, 0xFFFFFF00, 0xFF00FFFF, 0xFFFF00FF, 0xFF008866, 0xFF3D6F9E)
        for (dark in listOf(false, true)) for (sample in samples) {
            val palette = accentColors(sample.toInt(), dark)
            val background = (if (dark) 0xFF211F1C else 0xFFFAF8F2).toInt()
            assertTrue(contrast(palette.primary, background) >= 4.5)
            assertTrue(contrast(palette.primary, palette.onPrimary) >= 4.5)
            assertTrue(contrast(palette.container, palette.onContainer) >= 4.5)
        }
    }
    @Test fun customInputAcceptsOpaqueRgbAndRejectsIncompleteOrTransparentValues() {
        assertEquals(0xFF00AAbb, parseAccent("#00AAbb"))
        assertEquals(0xFF123456, parseAccent("123456"))
        for (input in listOf("", "#FFF", "#GG0011", "#00112233", " 123456", "#12345")) assertNull(parseAccent(input))
    }
}
