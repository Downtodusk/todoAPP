package com.example.todo.ui

import com.example.todo.ui.components.DetailBackGeometry
import org.junit.Assert.*
import org.junit.Test

class DetailBackGeometryTest {
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
