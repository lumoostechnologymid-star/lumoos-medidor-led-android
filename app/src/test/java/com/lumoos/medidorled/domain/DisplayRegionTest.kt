package com.lumoos.medidorled.domain

import com.lumoos.medidorled.camera.DisplayRegion
import org.junit.Assert.*
import org.junit.Test

class DisplayRegionTest {
    @Test fun squareFollowsOffsetCropAndRotation() {
        val landscape = DisplayRegion.regions(100, 200, 1100, 800)
        val portrait = DisplayRegion.regions(200, 100, 800, 1100)
        assertEquals(48, landscape.center.right - landscape.center.left)
        assertEquals(48, landscape.center.bottom - landscape.center.top)
        assertEquals(landscape.center.left, portrait.center.top)
        assertEquals(landscape.center.top, portrait.center.left)
        assertTrue(landscape.outer.left >= 100)
        assertTrue(landscape.outer.bottom <= 800)
    }

    @Test fun adjacentBlinkOutsideReferenceCannotTriggerMeasurement() {
        val regions = DisplayRegion.regions(0, 0, 1000, 600)
        fun read(centerVisible: Boolean, neighborVisible: Boolean) = DisplayRegion.measure(regions) { x, y ->
            when {
                centerVisible && regions.center.contains(x,y) -> 40
                neighborVisible && y >= regions.outer.bottom + 10 -> 0
                else -> 160
            }
        }!!
        assertEquals(0f, read(false, false).signal, 0f)
        assertEquals(0f, read(false, true).signal, 0f)
        assertEquals(120f, read(true, true).signal, 0f)
        assertEquals(120f, read(true, false).signal, 0f)
    }

    @Test fun absentPixelDataDoesNotBecomeAnAppearance() {
        val regions = DisplayRegion.regions(0,0,100,100)
        assertNull(DisplayRegion.measure(regions) { _, _ -> null })
    }
}
