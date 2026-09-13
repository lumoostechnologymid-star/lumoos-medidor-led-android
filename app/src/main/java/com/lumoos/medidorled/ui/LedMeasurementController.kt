package com.lumoos.medidorled.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumoos.medidorled.camera.LedFrameSample
import com.lumoos.medidorled.domain.LedMeasurementEngine

enum class LedColorMode {
    YELLOW,
    RED,
    INFRARED,
    DISPLAY
}

class LedMeasurementController {
    private var engine = LedMeasurementEngine(threshold = 20f, hysteresis = 2f)

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

    fun setLedColorMode(mode: LedColorMode) {
        if (state.locked || state.calibrating || state.ledColorMode == mode) return
        engine = LedMeasurementEngine(threshold = 20f, hysteresis = 2f)
        calibrationStartNs = null
        state = state.copy(
            ledColorMode = mode,
            threshold = 20f,
            hysteresis = 2f,
            signal = 0f,
            ledKnown = false,
            ledOn = false,
            revolutions = 0,
            elapsedSeconds = 0.0,
            lastRevolutionSeconds = 0.0,
            resultKw = null,
            status = "Listo para medir",
            message = when (mode) {
                LedColorMode.YELLOW -> "Modo amarillo activado · optimizado para destellos cortos"
                LedColorMode.RED -> "Modo rojo activado · optimizado para destellos cortos"
                LedColorMode.INFRARED -> "Modo infrarrojo activado · se detectará contraste de brillo"
                LedColorMode.DISPLAY -> "Centra solo el cuadrado superior. Visible → ausente → visible = una vuelta. Calibra antes de medir."
            }
        )
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
            message = if (state.isDisplay) "Mantén el cuadrado centrado y deja que aparezca y desaparezca durante 3 segundos" else "Mantén el LED dentro del recuadro y deja que parpadee durante 3 segundos"
        )
    }

    fun onFrame(sample: LedFrameSample) {
        // Ignore a queued frame from the previous detection mode.
        if (sample.sampledForDisplay != state.isDisplay) return
        val selectedSignal = when (state.ledColorMode) {
            LedColorMode.YELLOW -> sample.yellowSignal
            LedColorMode.RED -> sample.redSignal
            LedColorMode.INFRARED -> sample.infraredSignal
            LedColorMode.DISPLAY -> sample.displaySignal
        }

        var next = state.copy(
            brightness = sample.centerLuma,
            signal = selectedSignal
        )

        if (next.calibrating) {
            val start = calibrationStartNs ?: sample.timestampNs.also { calibrationStartNs = it }
            calibrationMin = minOf(calibrationMin, selectedSignal)
            calibrationMax = maxOf(calibrationMax, selectedSignal)
            val elapsed = (sample.timestampNs - start) / 1_000_000_000f
            next = next.copy(calibrationProgress = (elapsed / 3f).coerceIn(0f, 1f))

            if (elapsed >= 3f) {
                val range = calibrationMax - calibrationMin
                if (range >= 2f) {
                    val threshold = (calibrationMin + calibrationMax) / 2f
                    val hysteresis = (range * 0.15f).coerceIn(0.5f, 8f)
                    engine.setSensitivity(threshold, hysteresis)
                    next = next.copy(
                        calibrating = false,
                        threshold = threshold,
                        hysteresis = hysteresis,
                        calibrationProgress = 1f,
                        message = "Calibración lista · apagado ${calibrationMin.format1()} · encendido ${calibrationMax.format1()} · umbral ${threshold.format1()}"
                    )
                } else {
                    next = next.copy(
                        calibrating = false,
                        calibrationProgress = 0f,
                        message = if (next.ledColorMode == LedColorMode.INFRARED) {
                            "El cambio detectado fue muy pequeño. Acerca la cámara; algunos teléfonos filtran la luz IR."
                        } else {
                            if (next.isDisplay) "No se distinguieron ambos estados. Centra solo el cuadrado superior y repite cuando aparezca y desaparezca." else "El cambio detectado fue muy pequeño. Centra mejor el LED y vuelve a calibrar."
                        }
                    )
                }
                calibrationStartNs = null
            }
        }

        state = next
        apply(engine.onLuma(selectedSignal, sample.timestampNs), preserveMessage = true)
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
            status = if (state.isDisplay) snapshot.status
                .replace("el LED se apague", "el cuadrado desaparezca")
                .replace("LED apagado", "Cuadrado ausente")
                .replace("encendido", "aparición") else snapshot.status,
            threshold = snapshot.threshold,
            hysteresis = snapshot.hysteresis,
            message = if (preserveMessage) state.message else null
        )
    }
}

data class MeasurementUiState(
    val khText: String = "1.0",
    val targetRevolutions: Int = 5,
    val threshold: Float = 20f,
    val hysteresis: Float = 2f,
    val brightness: Float = 0f,
    val signal: Float = 0f,
    val ledColorMode: LedColorMode = LedColorMode.YELLOW,
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
    val isDisplay: Boolean get() = ledColorMode == LedColorMode.DISPLAY
    val locked: Boolean get() = armed || measuring
    val onThreshold: Float get() = (threshold + hysteresis).coerceAtMost(255f)
    val offThreshold: Float get() = (threshold - hysteresis).coerceAtLeast(0f)
}

private fun Float.format1(): String = String.format(java.util.Locale.US, "%.1f", this)

