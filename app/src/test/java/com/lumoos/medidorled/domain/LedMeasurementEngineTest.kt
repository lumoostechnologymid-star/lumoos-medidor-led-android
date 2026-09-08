package com.lumoos.medidorled.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LedMeasurementEngineTest {
    @Test
    fun countsOnOffOnAsOneRevolution() {
        val engine = LedMeasurementEngine(threshold = 100f, hysteresis = 5f, debounceNs = 1L)
        var t = 0L
        fun sample(luma: Float, deltaNs: Long = 100_000_000L) {
            t += deltaNs
            engine.onLuma(luma, t)
            t += 2L
            engine.onLuma(luma, t)
        }

        sample(20f)
        engine.arm(targetRevolutions = 1, kh = 1.0)
        sample(150f)
        sample(20f)
        sample(150f, 1_000_000_000L)

        val snapshot = engine.onLuma(150f, t + 2L)
        assertEquals(1, snapshot.revolutions)
        assertNotNull(snapshot.resultKw)
    }
}
