package com.example.todo.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LineIcons {
    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()
    val Add = icon("Add") { moveTo(12f, 4f); lineTo(12f, 20f); moveTo(4f, 12f); lineTo(20f, 12f) }
    val Back = icon("Back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val Check = icon("Check") { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }
    val Calendar = icon("Calendar") { moveTo(5f, 5f); lineTo(19f, 5f); lineTo(19f, 21f); lineTo(5f, 21f); close(); moveTo(8f, 3f); lineTo(8f, 7f); moveTo(16f, 3f); lineTo(16f, 7f); moveTo(5f, 10f); lineTo(19f, 10f) }
    val Settings = icon("Settings") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f); moveTo(8f, 3f); lineTo(8f, 9f); moveTo(16f, 9f); lineTo(16f, 15f); moveTo(10f, 15f); lineTo(10f, 21f) }
    val Delete = icon("Delete") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f); moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f); moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f) }
    val Forward = icon("Forward") { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
    val Down = icon("Down") { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
    val More = ImageVector.Builder("More", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), stroke = null) {
            for (y in listOf(5f, 12f, 19f)) {
                moveTo(13.5f, y); arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f); close()
            }
        }
    }.build()
    val Menu = icon("Menu") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f) }
    val Clock = icon("Clock") { moveTo(21f, 12f); arcToRelative(9f, 9f, 0f, true, true, -18f, 0f); arcToRelative(9f, 9f, 0f, true, true, 18f, 0f); moveTo(12f, 6f); lineTo(12f, 12f); lineTo(16f, 14f) }
    val Location = icon("Location") { moveTo(12f, 22f); curveTo(9f, 17f, 5f, 13f, 5f, 9f); curveTo(5f, 0f, 19f, 0f, 19f, 9f); curveTo(19f, 13f, 15f, 17f, 12f, 22f); close(); moveTo(15f, 9f); arcToRelative(3f, 3f, 0f, true, true, -6f, 0f); arcToRelative(3f, 3f, 0f, true, true, 6f, 0f) }
    val Note = icon("Note") { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(14f, 18f) }
    val Bell = icon("Bell") { moveTo(5f, 16f); lineTo(7f, 13f); lineTo(7f, 8f); curveTo(7f, 1f, 17f, 1f, 17f, 8f); lineTo(17f, 13f); lineTo(19f, 16f); lineTo(5f, 16f); moveTo(10f, 20f); curveTo(11f, 22f, 13f, 22f, 14f, 20f) }
    val Repeat = icon("Repeat") { moveTo(4f, 10f); lineTo(4f, 6f); lineTo(20f, 6f); moveTo(17f, 3f); lineTo(20f, 6f); lineTo(17f, 9f); moveTo(20f, 14f); lineTo(20f, 18f); lineTo(4f, 18f); moveTo(7f, 15f); lineTo(4f, 18f); lineTo(7f, 21f) }
}
