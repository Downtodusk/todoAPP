package com.example.todo.ui
import androidx.compose.ui.geometry.Rect
import com.example.todo.ui.components.TaskReturnPhases
import org.junit.Assert.*
import org.junit.Test

class TaskReturnPhasesTest {
    @Test fun anAlreadyShrunkGestureDoesNotPauseBeforeRetracting() {
        assertEquals(110, TaskReturnPhases.remainingPrelude(0f))
        assertEquals(0, TaskReturnPhases.remainingPrelude(1f))
        assertTrue(TaskReturnPhases.remainingPrelude(.75f) < 30)
    }
    @Test fun contentHandoffNeverShowsBothTexts() {
        for (i in -10..110) {
            val p = i / 100f
            val detail = TaskReturnPhases.detailAlpha(p); val row = TaskReturnPhases.rowAlpha(p)
            assertTrue(detail in 0f..1f); assertTrue(row in 0f..1f)
            assertEquals(0f, detail * row, 0f)
        }
        assertEquals(1f, TaskReturnPhases.detailAlpha(0f), 0f)
        assertEquals(1f, TaskReturnPhases.rowAlpha(1f), .0001f)
    }
    @Test fun finalBoundsMatchUpperLowerRowsAndCenterFallbackExactly() {
        val from = Rect(20f, 40f, 980f, 1800f)
        for (target in listOf(Rect(30f, 140f, 970f, 240f), Rect(30f, 1400f, 970f, 1510f), Rect(500f, 900f, 500f, 900f))) {
            assertEquals(from, TaskReturnPhases.interpolate(from, target, 0f))
            assertEquals(target, TaskReturnPhases.interpolate(from, target, 1f))
        }
    }
}
