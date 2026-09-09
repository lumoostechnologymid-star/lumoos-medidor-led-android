package com.lumoos.medidorled.domain

import com.lumoos.medidorled.camera.DisplayContrast
import org.junit.Assert.*
import org.junit.Test

class DisplayMeasurementTest {
    @Test fun darkSquareUsesFullCyclesAndSameKh() {
        val engine = LedMeasurementEngine(threshold = 20f, hysteresis = 2f, debounceNs = 1L)
        fun sample(center: Float, ambient: Float, time: Long): LedMeasurementEngine.Snapshot {
            val signal = DisplayContrast.signal(center, ambient)
            engine.onLuma(signal, time)
            return engine.onLuma(signal, time + 1L)
        }
        sample(150f, 150f, 0L)
        engine.arm(1, 21.6)
        assertTrue(sample(70f, 150f, 1_000_000_000L).measuring)
        // Uniform lighting changes do not count as another appearance.
        assertEquals(0, sample(40f, 120f, 2_000_000_000L).revolutions)
        val absent = sample(120f, 120f, 3_000_000_000L)
        assertTrue(absent.measuring)
        assertEquals(0, absent.revolutions)
        val result = sample(70f, 150f, 5_000_000_000L)
        assertEquals(1, result.revolutions)
        assertEquals(4.0, result.elapsedSeconds, 0.000001)
        assertEquals(19.44, result.resultKw!!, 0.000001)
        assertFalse(result.measuring)
    }

    @Test fun brightLedIsNotADarkSquare() {
        assertEquals(0f, DisplayContrast.signal(200f, 100f), 0f)
        assertEquals(0f, DisplayContrast.signal(120f, 120f), 0f)
        assertEquals(80f, DisplayContrast.signal(40f, 120f), 0f)
    }
}
