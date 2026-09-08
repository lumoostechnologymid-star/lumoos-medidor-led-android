package com.lumoos.medidorled.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumoos.medidorled.domain.LedMeasurementEngine

class LedMeasurementController {
    private val engine = LedMeasurementEngine()

    var state by mutableStateOf(MeasurementUiState())
        private set

    private var calibrationStartNs: Long? = null
    private var calibrationMin = 255f
    private var calibrationMax = 0f

    fun setKh(text: String) {
        if (state.locked) return
        state = state.copy(khText = text.filter { it.isDigit() || it == '.' }.take(12))
    }

    fun setTargetRevolutions(value: Int) {
        if (state.locked) return
        state = state.copy(targetRevolutions = value.coerceIn(1, 999))
    }

    fun setThreshold(value: Float) {
        if (state.locked || state.calibrating) return
        engine.setSensitivity(value)
        state = state.copy(threshold = value)
    }

    fun armMeasurement() {
        val kh = state.khText.toDoubleOrNull()
        if (kh == null || kh <= 0.0) {
            state = state.copy(message = "Ingresa una KH válida mayor que 0")
            return
        }
        if (state.calibrating) return
        apply(engine.arm(state.targetRevolutions, kh))
        state = state.copy(message = null)
    }

    fun reset() {
        apply(engine.reset())
        state = state.copy(message = null)
    }

    fun startCalibration() {
        if (state.locked) return
        calibrationStartNs = null
        calibrationMin = 255f
        calibrationMax = 0f
        state = state.copy(
            calibrating = true,
            calibrationProgress = 0f,
            message = "Mantén el recuadro sobre el LED durante 3 segundos"
        )
    }

    fun onLuma(luma: Float, timestampNs: Long) {
        var next = state.copy(brightness = luma)

        if (next.calibrating) {
            val start = calibrationStartNs ?: timestampNs.also { calibrationStartNs = it }
            calibrationMin = minOf(calibrationMin, luma)
            calibrationMax = maxOf(calibrationMax, luma)
            val elapsed = (timestampNs - start) / 1_000_000_000f
            next = next.copy(calibrationProgress = (elapsed / 3f).coerceIn(0f, 1f))

            if (elapsed >= 3f) {
                val range = calibrationMax - calibrationMin
                if (range >= 15f) {
                    val threshold = (calibrationMin + calibrationMax) / 2f
                    engine.setSensitivity(threshold)
                    next = next.copy(
                        calibrating = false,
                        threshold = threshold,
                        calibrationProgress = 1f,
                        message = "Calibración lista · umbral ${threshold.toInt()}"
                    )
                } else {
                    next = next.copy(
                        calibrating = false,
                        calibrationProgress = 0f,
                        message = "No detecté suficiente cambio de luz. Acerca más la cámara al LED e intenta de nuevo."
                    )
                }
                calibrationStartNs = null
            }
        }

        state = next
        apply(engine.onLuma(luma, timestampNs), preserveMessage = true)
    }

    private fun apply(snapshot: LedMeasurementEngine.Snapshot, preserveMessage: Boolean = false) {
        state = state.copy(
            ledOn = snapshot.ledOn,
            ledKnown = snapshot.ledKnown,
            armed = snapshot.armed,
            measuring = snapshot.measuring,
            revolutions = snapshot.revolutions,
            elapsedSeconds = snapshot.elapsedSeconds,
            lastRevolutionSeconds = snapshot.lastRevolutionSeconds,
            resultKw = snapshot.resultKw,
            status = snapshot.status,
            threshold = snapshot.threshold,
            message = if (preserveMessage) state.message else null
        )
    }
}

data class MeasurementUiState(
    val khText: String = "1.0",
    val targetRevolutions: Int = 5,
    val threshold: Float = 140f,
    val brightness: Float = 0f,
    val ledOn: Boolean = false,
    val ledKnown: Boolean = false,
    val armed: Boolean = false,
    val measuring: Boolean = false,
    val revolutions: Int = 0,
    val elapsedSeconds: Double = 0.0,
    val lastRevolutionSeconds: Double = 0.0,
    val resultKw: Double? = null,
    val status: String = "Listo para medir",
    val calibrating: Boolean = false,
    val calibrationProgress: Float = 0f,
    val message: String? = null
) {
    val locked: Boolean get() = armed || measuring
}
