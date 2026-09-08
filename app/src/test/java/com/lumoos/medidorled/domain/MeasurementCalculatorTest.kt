package com.lumoos.medidorled.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementCalculatorTest {
    @Test
    fun fiveRevolutionsKh1In10SecondsEquals1Point8Kw() {
        assertEquals(1.8, MeasurementCalculator.calculateKw(5, 1.0, 10.0), 0.000001)
    }
}
