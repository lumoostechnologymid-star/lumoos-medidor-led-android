package com.lumoos.medidorled.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

/**
 * Analiza una zona pequeña al centro y la compara con un anillo alrededor.
 * Esto ayuda a rechazar cambios de iluminación ambiente (por ejemplo, sol o nubes),
 * porque esos cambios afectan tanto el centro como el entorno, mientras que el LED
 * debe estar concentrado dentro del recuadro central.
 *
 * No se guarda ningún fotograma ni se envía ninguna imagen fuera del teléfono.
 */
class LedFrameAnalyzer(
    private val centerFraction: Float = 0.12f,
    private val ambientFraction: Float = 0.30f,
    private val onSample: (LedFrameSample) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val uPlane = image.planes.getOrNull(1)
            val vPlane = image.planes.getOrNull(2)
            val width = image.width
            val height = image.height

            val centerWidth = (width * centerFraction).toInt().coerceAtLeast(8)
            val centerHeight = (height * centerFraction).toInt().coerceAtLeast(8)
            val outerWidth = (width * ambientFraction).toInt().coerceAtLeast(centerWidth + 8)
            val outerHeight = (height * ambientFraction).toInt().coerceAtLeast(centerHeight + 8)

            val centerLeft = ((width - centerWidth) / 2).coerceAtLeast(0)
            val centerTop = ((height - centerHeight) / 2).coerceAtLeast(0)
            val centerRight = (centerLeft + centerWidth).coerceAtMost(width)
            val centerBottom = (centerTop + centerHeight).coerceAtMost(height)

            val outerLeft = ((width - outerWidth) / 2).coerceAtLeast(0)
            val outerTop = ((height - outerHeight) / 2).coerceAtLeast(0)
            val outerRight = (outerLeft + outerWidth).coerceAtMost(width)
            val outerBottom = (outerTop + outerHeight).coerceAtMost(height)

            var centerLuma = 0.0
            var centerRed = 0.0
            var centerYellow = 0.0
            var centerCount = 0

            var ambientLuma = 0.0
            var ambientRed = 0.0
            var ambientYellow = 0.0
            var ambientCount = 0

            val sampleStep = 3
            var y = outerTop
            while (y < outerBottom) {
                var x = outerLeft
                while (x < outerRight) {
                    val yValue = readPlaneValue(yPlane, x, y)
                    if (yValue != null) {
                        val rgb = if (uPlane != null && vPlane != null) {
                            val uValue = readPlaneValue(uPlane, x / 2, y / 2)
                            val vValue = readPlaneValue(vPlane, x / 2, y / 2)
                            if (uValue != null && vValue != null) {
                                yuvToRgb(yValue, uValue, vValue)
                            } else {
                                Rgb(yValue.toFloat(), yValue.toFloat(), yValue.toFloat())
                            }
                        } else {
                            Rgb(yValue.toFloat(), yValue.toFloat(), yValue.toFloat())
                        }

                        val redScore = (rgb.r - max(rgb.g, rgb.b) * 0.90f).coerceAtLeast(0f)
                        val yellowScore = (min(rgb.r, rgb.g) - rgb.b * 0.85f).coerceAtLeast(0f)
                        val isCenter = x in centerLeft until centerRight && y in centerTop until centerBottom

                        if (isCenter) {
                            centerLuma += yValue
                            centerRed += redScore
                            centerYellow += yellowScore
                            centerCount++
                        } else {
                            ambientLuma += yValue
                            ambientRed += redScore
                            ambientYellow += yellowScore
                            ambientCount++
                        }
                    }
                    x += sampleStep
                }
                y += sampleStep
            }

            if (centerCount == 0) return

            val cLuma = (centerLuma / centerCount).toFloat()
            val cRed = (centerRed / centerCount).toFloat()
            val cYellow = (centerYellow / centerCount).toFloat()

            val aLuma = if (ambientCount > 0) (ambientLuma / ambientCount).toFloat() else cLuma
            val aRed = if (ambientCount > 0) (ambientRed / ambientCount).toFloat() else cRed
            val aYellow = if (ambientCount > 0) (ambientYellow / ambientCount).toFloat() else cYellow

            val localLumaContrast = cLuma - aLuma
            val redSignal = normalizeContrast((cRed - aRed) * 1.55f + localLumaContrast * 0.30f)
            val yellowSignal = normalizeContrast((cYellow - aYellow) * 1.55f + localLumaContrast * 0.30f)
            val infraredSignal = normalizeContrast(localLumaContrast * 1.80f)

            onSample(
                LedFrameSample(
                    centerLuma = cLuma,
                    redSignal = redSignal,
                    yellowSignal = yellowSignal,
                    infraredSignal = infraredSignal,
                    timestampNs = image.imageInfo.timestamp
                )
            )
        } finally {
            image.close()
        }
    }

    private fun readPlaneValue(plane: ImageProxy.PlaneProxy, x: Int, y: Int): Int? {
        val index = y * plane.rowStride + x * plane.pixelStride
        val buffer = plane.buffer
        if (index !in 0 until buffer.limit()) return null
        return buffer.get(index).toInt() and 0xFF
    }

    private fun yuvToRgb(y: Int, u: Int, v: Int): Rgb {
        val yf = ((y - 16).coerceAtLeast(0)) * 1.164f
        val uf = (u - 128).toFloat()
        val vf = (v - 128).toFloat()
        val r = (yf + 1.596f * vf).coerceIn(0f, 255f)
        val g = (yf - 0.392f * uf - 0.813f * vf).coerceIn(0f, 255f)
        val b = (yf + 2.017f * uf).coerceIn(0f, 255f)
        return Rgb(r, g, b)
    }

    private fun normalizeContrast(contrast: Float): Float =
        (128f + contrast).coerceIn(0f, 255f)

    private data class Rgb(val r: Float, val g: Float, val b: Float)
}

data class LedFrameSample(
    val centerLuma: Float,
    val redSignal: Float,
    val yellowSignal: Float,
    val infraredSignal: Float,
    val timestampNs: Long
)
