package com.example.todo.ui

import com.example.todo.ui.components.DetailBackGeometry
import org.junit.Assert.*
import org.junit.Test

class DetailBackGeometryTest {
    @Test fun systemProgressIsVisibleEarlyAndEasesIntoItsLimit() {
        assertEquals(0f, DetailBackGeometry.gestureProgress(0f), 0f)
        assertEquals(.75f, DetailBackGeometry.gestureProgress(.3f), .001f)
        assertEquals(1f, DetailBackGeometry.gestureProgress(1f), 0f)
        var previous = 0f
        for (i in 0..100) {
            val value = DetailBackGeometry.gestureProgress(i / 100f)
            assertTrue(value >= previous && value <= 1f); previous = value
        }
    }
    @Test fun extremeDragNeverShrinksPastNinetyPercent() {
        for (i in -10..110) {
            val progress = i / 100f
            assertTrue(DetailBackGeometry.scale(progress) in .90f..1f)
            assertTrue(DetailBackGeometry.radius(progress) in 0f..28f)
            assertEquals(-DetailBackGeometry.horizontal(progress, 0), DetailBackGeometry.horizontal(progress, 1), .001f)
        }
        assertEquals(1f, DetailBackGeometry.scale(0f), 0f)
        assertEquals(.90f, DetailBackGeometry.scale(1f), .0001f)
    }
}
