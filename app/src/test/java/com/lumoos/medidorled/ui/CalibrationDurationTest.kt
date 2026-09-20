package com.lumoos.medidorled.ui

import com.lumoos.medidorled.camera.LedFrameSample
import org.junit.Assert.*
import org.junit.Test

class CalibrationDurationTest {
    private fun frame(controller: LedMeasurementController, seconds: Double, signal: Float) {
        controller.onFrame(LedFrameSample(
            centerLuma = signal, redSignal = signal, yellowSignal = signal,
            infraredSignal = signal, timestampNs = (seconds * 1_000_000_000L).toLong(),
            displaySignal = signal, sampledForDisplay = controller.state.isDisplay
        ))
    }

    @Test fun everyDurationWaitsForItsDeadlineInBothModes() {
        for (mode in listOf(LedColorMode.YELLOW, LedColorMode.DISPLAY)) {
            for (duration in listOf(3, 5, 10, 15)) {
                val c = LedMeasurementController()
                c.setLedColorMode(mode)
                c.setCalibrationSeconds(duration)
                c.startCalibration()
                frame(c, 1.0, 10f)
                frame(c, duration.toDouble() + 0.9, 90f)
                assertTrue(c.state.calibrating)
                frame(c, duration.toDouble() + 1.0, 10f)
                assertFalse(c.state.calibrating)
                assertEquals(50f, c.state.threshold, 0.01f)
            }
        }
    }

    @Test fun slowPulseIsIncludedAndDurationCannotChangeDuringCalibration() {
        val c = LedMeasurementController()
        c.setCalibrationSeconds(15)
        c.startCalibration()
        frame(c, 1.0, 10f)
        frame(c, 4.0, 10f)
        assertTrue(c.state.calibrating)
        c.setCalibrationSeconds(3)
        assertEquals(15, c.state.calibrationSeconds)
        frame(c, 14.0, 90f)
        frame(c, 16.0, 10f)
        assertEquals(50f, c.state.threshold, 0.01f)
    }

    @Test fun resetCancelsCalibrationAndNoPulseKeepsPreviousSensitivity() {
        val c = LedMeasurementController()
        c.setCalibrationSeconds(10)
        c.startCalibration()
        frame(c, 1.0, 10f)
        c.reset()
        frame(c, 12.0, 90f)
        assertFalse(c.state.calibrating)
        assertEquals(20f, c.state.threshold, 0f)
        c.startCalibration()
        frame(c, 20.0, 10f)
        frame(c, 30.0, 10f)
        assertFalse(c.state.calibrating)
        assertEquals(20f, c.state.threshold, 0f)
    }
}
