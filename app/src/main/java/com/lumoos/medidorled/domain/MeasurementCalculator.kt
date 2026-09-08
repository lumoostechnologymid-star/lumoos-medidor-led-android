package com.lumoos.medidorled.domain

object MeasurementCalculator {
    fun calculateKw(revolutions: Int, kh: Double, timeSeconds: Double): Double {
        require(revolutions > 0) { "revolutions must be greater than zero" }
        require(kh > 0.0) { "kh must be greater than zero" }
        require(timeSeconds > 0.0) { "timeSeconds must be greater than zero" }
        return (revolutions * kh * 3.6) / timeSeconds
    }
}
