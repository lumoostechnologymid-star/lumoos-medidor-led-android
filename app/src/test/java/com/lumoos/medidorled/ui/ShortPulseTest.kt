package com.lumoos.medidorled.ui

import com.lumoos.medidorled.camera.LedFrameSample
import org.junit.Assert.*
import org.junit.Test

class ShortPulseTest {
    private fun sample(c: LedMeasurementController, signal: Float, ms: Long) {
        c.onFrame(LedFrameSample(
            centerLuma = 110f, redSignal = signal, yellowSignal = signal,
            infraredSignal = signal, timestampNs = ms * 1_000_000L,
            displaySignal = signal, sampledForDisplay = c.state.isDisplay
        ))
    }

    @Test fun singleFramePulsesAreCountedInAllLedModes() {
        for (mode in listOf(LedColorMode.YELLOW, LedColorMode.RED, LedColorMode.INFRARED)) {
            val c = LedMeasurementController()
            c.setLedColorMode(mode)
            c.setThreshold(92f)
            c.setTargetRevolutions(3)
            sample(c, 10f, 0)
            sample(c, 10f, 33)
            c.armMeasurement()
            // Video: high ~155, low ~10; ~800 ms between flashes.
            for (t in listOf(100L, 900L, 1700L, 2500L)) {
                sample(c, 155.4f, t)
                sample(c, 10f, t + 33)
            }
            assertEquals(3, c.state.revolutions)
            assertEquals(2.4, c.state.elapsedSeconds, 0.00001)
            assertEquals(0.8, c.state.lastRevolutionSeconds, 0.00001)
            assertEquals(4.5, c.state.resultKw!!, 0.00001)
        }
    }

    @Test fun hysteresisPreventsDoubleCountsAndStartingOnWaitsForOff() {
        val c = LedMeasurementController()
        c.setThreshold(92f)
        sample(c, 155f, 0)
        c.armMeasurement()
        sample(c, 155f, 33)
        assertFalse(c.state.measuring)
        sample(c, 10f, 66)
        sample(c, 155f, 100)
        for (i in 1..10) sample(c, if (i % 2 == 0) 91f else 93f, 100L + i * 33)
        assertEquals(0, c.state.revolutions)
        sample(c, 10f, 700)
        sample(c, 155f, 900)
        assertEquals(1, c.state.revolutions)
    }

    @Test fun stableAndDisplayModesStillRejectIsolatedHighFrames() {
        for (display in listOf(false, true)) {
            val c = LedMeasurementController()
            if (display) c.setLedColorMode(LedColorMode.DISPLAY) else c.setShortPulses(false)
            sample(c, 0f, 0)
            sample(c, 0f, 33)
            c.armMeasurement()
            sample(c, 150f, 100)
            sample(c, 0f, 133)
            assertFalse(c.state.measuring)
            sample(c, 150f, 900)
            sample(c, 150f, 933)
            assertTrue(c.state.measuring)
        }
    }

    @Test fun cannotChangeResponseDuringMeasurementOrCalibration() {
        val c = LedMeasurementController()
        c.startCalibration()
        c.setShortPulses(false)
        assertTrue(c.state.shortPulses)
        c.reset()
        c.armMeasurement()
        c.setShortPulses(false)
        assertTrue(c.state.shortPulses)
    }
}
