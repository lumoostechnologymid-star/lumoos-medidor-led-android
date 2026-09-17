package com.lumoos.medidorled.domain

import com.lumoos.medidorled.camera.LedColorSignal
import org.junit.Assert.*
import org.junit.Test

class LedColorSignalTest {
    @Test fun neutralSunlightDoesNotBecomeYellow() {
        for (v in listOf(0f, 32f, 128f, 255f)) {
            assertEquals(0f, LedColorSignal.yellowGreen(v, v, v), 0.001f)
        }
    }

    @Test fun detectsGreenYellowEvenWhenDarkerThanWhiteBackground() {
        assertTrue(LedColorSignal.yellowGreen(95f, 130f, 35f) > 100f)
        assertEquals(0f, LedColorSignal.yellowGreen(220f, 220f, 220f), 0.001f)
    }

    @Test fun toleratesExposureScaling() {
        val full = LedColorSignal.yellowGreen(100f, 140f, 40f)
        val dim = LedColorSignal.yellowGreen(50f, 70f, 20f)
        assertEquals(full, dim, 0.001f)
    }

    @Test fun rejectsRedBlueAndLimitsDarkNoise() {
        assertEquals(0f, LedColorSignal.yellowGreen(240f, 20f, 20f), 0.001f)
        assertEquals(0f, LedColorSignal.yellowGreen(20f, 20f, 240f), 0.001f)
        assertTrue(LedColorSignal.yellowGreen(1f, 2f, 1f) < 10f)
    }
}
