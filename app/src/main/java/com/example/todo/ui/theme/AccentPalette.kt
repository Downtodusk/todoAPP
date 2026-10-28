package com.example.todo.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

internal fun parseAccent(text: String): Long? = text.removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.or(0xFF000000)
internal fun blendColor(first: Int, second: Int, fraction: Double): Int {
    var out = 0xFF000000.toInt()
    for (shift in listOf(16, 8, 0)) {
        val value = (((first ushr shift) and 255) * (1 - fraction) + ((second ushr shift) and 255) * fraction).roundToInt()
        out = out or (value.coerceIn(0, 255) shl shift)
    }
    return out
}
internal fun contrast(first: Int, second: Int): Double {
    fun luminance(color: Int): Double = listOf(16 to .2126, 8 to .7152, 0 to .0722).sumOf { (shift, weight) ->
        val channel = ((color ushr shift) and 255) / 255.0
        weight * if (channel <= .04045) channel / 12.92 else ((channel + .055) / 1.055).pow(2.4)
    }
    val a = luminance(first); val b = luminance(second)
    return (maxOf(a, b) + .05) / (minOf(a, b) + .05)
}
internal data class AccentColors(val primary: Int, val onPrimary: Int, val container: Int, val onContainer: Int)
internal fun accentColors(seed: Int, dark: Boolean): AccentColors {
    val background = if (dark) 0xFF211F1C.toInt() else 0xFFFAF8F2.toInt()
    val destination = if (dark) -1 else 0xFF000000.toInt()
    var primary = seed
    for (step in 0..100) {
        primary = blendColor(seed, destination, step / 100.0)
        if (contrast(primary, background) >= 4.5) break
    }
    val onPrimary = if (contrast(primary, -1) >= contrast(primary, 0xFF000000.toInt())) -1 else 0xFF000000.toInt()
    val container = blendColor(background, seed, if (dark) .24 else .12)
    val onContainer = if (contrast(container, -1) >= contrast(container, 0xFF000000.toInt())) -1 else 0xFF000000.toInt()
    return AccentColors(primary, onPrimary, container, onContainer)
}
