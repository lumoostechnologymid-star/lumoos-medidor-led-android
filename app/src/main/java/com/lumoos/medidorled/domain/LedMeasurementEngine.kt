package com.lumoos.medidorled.domain

import kotlin.math.max

/**
 * State machine for the meter LED.
 *
 * A revolution is defined as: ON -> OFF -> ON.
 * When armed, the engine waits for a clean OFF -> ON edge to start the timer.
 * Every following OFF -> ON edge completes one revolution.
 */
class LedMeasurementEngine(
    threshold: Float = 20f,
    hysteresis: Float = 2f,
    // El video real del medidor muestra pulsos de aprox. 30–50 ms.
    // 12 ms exige al menos dos cuadros consecutivos a 60 fps, pero no pierde el pulso.
    private val debounceNs: Long = 12_000_000L
) {
    var threshold: Float = threshold
        private set
    var hysteresis: Float = hysteresis
        private set

    private var stableLedOn: Boolean? = null
    private var candidateLedOn: Boolean? = null
    private var candidateSinceNs: Long = 0L

    private var armed = false
    private var measuring = false
    private var seenOffSinceArm = false
    private var startNs: Long? = null
    private var lastOnNs: Long? = null
    private var revolutions = 0
    private var targetRevolutions = 1
    private var kh = 1.0
    private var elapsedSeconds = 0.0
    private var lastRevolutionSeconds = 0.0
    private var resultKw: Double? = null
    private var status = "Listo para medir"

    fun setSensitivity(newThreshold: Float, newHysteresis: Float = hysteresis) {
        threshold = newThreshold.coerceIn(0f, 255f)
        hysteresis = newHysteresis.coerceIn(0.2f, 40f)
    }

    fun arm(targetRevolutions: Int, kh: Double): Snapshot {
        require(targetRevolutions > 0)
        require(kh > 0.0)

        this.targetRevolutions = targetRevolutions
        this.kh = kh
        armed = true
        measuring = false
        revolutions = 0
        elapsedSeconds = 0.0
        lastRevolutionSeconds = 0.0
        resultKw = null
        startNs = null
        lastOnNs = null
        seenOffSinceArm = stableLedOn == false
        status = if (stableLedOn == true) {
            "Esperando que el LED se apague"
        } else {
            "Esperando el primer encendido"
        }
        return snapshot()
    }

    fun reset(): Snapshot {
        armed = false
        measuring = false
        seenOffSinceArm = false
        startNs = null
        lastOnNs = null
        revolutions = 0
        elapsedSeconds = 0.0
        lastRevolutionSeconds = 0.0
        resultKw = null
        status = "Listo para medir"
        return snapshot()
    }

    fun onLuma(luma: Float, timestampNs: Long): Snapshot {
        val desiredState = when (stableLedOn) {
            true -> luma >= threshold - hysteresis
            false -> luma >= threshold + hysteresis
            null -> luma >= threshold
        }

        if (candidateLedOn != desiredState) {
            candidateLedOn = desiredState
            candidateSinceNs = timestampNs
        }

        if (stableLedOn != desiredState && timestampNs - candidateSinceNs >= debounceNs) {
            stableLedOn = desiredState
            onStableTransition(desiredState, timestampNs)
        }

        if (measuring) {
            startNs?.let { start ->
                elapsedSeconds = max(0.0, (timestampNs - start) / 1_000_000_000.0)
            }
        }

        return snapshot()
    }

    private fun onStableTransition(isOn: Boolean, timestampNs: Long) {
        if (!armed) return

        if (!isOn) {
            seenOffSinceArm = true
            status = if (measuring) {
                "LED apagado · esperando siguiente encendido"
            } else {
                "LED apagado · esperando primer encendido"
            }
            return
        }

        if (!measuring) {
            if (seenOffSinceArm) {
                measuring = true
                startNs = timestampNs
                lastOnNs = timestampNs
                elapsedSeconds = 0.0
                status = "Medición iniciada · 0/$targetRevolutions vueltas"
            }
            return
        }

        val previousOn = lastOnNs ?: return
        revolutions += 1
        lastRevolutionSeconds = (timestampNs - previousOn) / 1_000_000_000.0
        lastOnNs = timestampNs
        elapsedSeconds = startNs?.let { (timestampNs - it) / 1_000_000_000.0 } ?: 0.0

        if (revolutions >= targetRevolutions) {
            resultKw = MeasurementCalculator.calculateKw(revolutions, kh, elapsedSeconds)
            measuring = false
            armed = false
            status = "Medición finalizada"
        } else {
            status = "Vuelta $revolutions/$targetRevolutions registrada"
        }
    }

    private fun snapshot() = Snapshot(
        ledOn = stableLedOn == true,
        ledKnown = stableLedOn != null,
        armed = armed,
        measuring = measuring,
        revolutions = revolutions,
        targetRevolutions = targetRevolutions,
        elapsedSeconds = elapsedSeconds,
        lastRevolutionSeconds = lastRevolutionSeconds,
        resultKw = resultKw,
        status = status,
        threshold = threshold,
        hysteresis = hysteresis
    )

    data class Snapshot(
        val ledOn: Boolean,
        val ledKnown: Boolean,
        val armed: Boolean,
        val measuring: Boolean,
        val revolutions: Int,
        val targetRevolutions: Int,
        val elapsedSeconds: Double,
        val lastRevolutionSeconds: Double,
        val resultKw: Double?,
        val status: String,
        val threshold: Float,
        val hysteresis: Float
    )
}
