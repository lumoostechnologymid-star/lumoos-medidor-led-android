package com.lumoos.medidorled.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

/**
 * Analiza una zona pequeña al centro y la compara con un anillo alrededor.
 * Para LEDs pequeños no promedia todo el centro: usa el promedio de los píxeles
 * más intensos, evitando que el destello se diluya dentro del recuadro.
 */
class LedFrameAnalyzer(
    private val centerFraction: Float = 0.10f,
    private val ambientFraction: Float = 0.28f,
    private val onSample: (LedFrameSample) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile var displayMode: Boolean = false

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes.firstOrNull() ?: return
            val uPlane = image.planes.getOrNull(1)
            val vPlane = image.planes.getOrNull(2)
            // Capture the mode once, so one frame cannot mix two ROI sizes.
            val forDisplay = displayMode
            if (forDisplay) {
                val crop = image.cropRect
                if (crop.width() < 8 || crop.height() < 8) return
                val regions = DisplayRegion.regions(crop.left, crop.top, crop.right, crop.bottom)
                val reading = DisplayRegion.measure(regions) { x, y -> readPlaneValue(yPlane, x, y) } ?: return
                onSample(LedFrameSample(
                    centerLuma = reading.centerLuma,
                    redSignal = 0f,
                    yellowSignal = 0f,
                    infraredSignal = 0f,
                    timestampNs = image.imageInfo.timestamp,
                    displaySignal = reading.signal,
                    sampledForDisplay = true
                ))
                return
            }
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

            val topLuma = TopAverage(16)
            val topRed = TopAverage(16)
            val topYellow = TopAverage(16)

            var centerLumaSum = 0.0
            var centerCount = 0
            var ambientLuma = 0.0
            var ambientRed = 0.0
            var ambientYellow = 0.0
            var ambientCount = 0

            val sampleStep = 2
            var y = outerTop
            while (y < outerBottom) {
                var x = outerLeft
                while (x < outerRight) {
                    val yValue = readPlaneValue(yPlane, x, y)
                    if (yValue != null) {
                        val rgb = if (uPlane != null && vPlane != null) {
                            val uValue = readPlaneValue(uPlane, x / 2, y / 2)
                            val vValue = readPlaneValue(vPlane, x / 2, y / 2)
                            if (uValue != null && vValue != null) yuvToRgb(yValue, uValue, vValue)
                            else Rgb(yValue.toFloat(), yValue.toFloat(), yValue.toFloat())
                        } else {
                            Rgb(yValue.toFloat(), yValue.toFloat(), yValue.toFloat())
                        }

                        val redScore = (rgb.r - max(rgb.g, rgb.b) * 0.90f).coerceAtLeast(0f)
                        val yellowScore = (min(rgb.r, rgb.g) - rgb.b * 0.75f).coerceAtLeast(0f)
                        val isCenter = x in centerLeft until centerRight && y in centerTop until centerBottom

                        if (isCenter) {
                            centerLumaSum += yValue
                            centerCount++
                            topLuma.add(yValue.toFloat())
                            topRed.add(redScore)
                            topYellow.add(yellowScore)
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

            if (topLuma.count == 0) return

            val cLuma = topLuma.average()
            val cRed = topRed.average()
            val cYellow = topYellow.average()

            val aLuma = if (ambientCount > 0) (ambientLuma / ambientCount).toFloat() else 0f
            val aRed = if (ambientCount > 0) (ambientRed / ambientCount).toFloat() else 0f
            val aYellow = if (ambientCount > 0) (ambientYellow / ambientCount).toFloat() else 0f

            // Señal real 0..255, sin sumar 128. Esto hace visibles los valores bajos reales.
            val localLumaContrast = (cLuma - aLuma).coerceAtLeast(0f)
            val redSignal = ((cRed - aRed) * 1.20f + localLumaContrast * 0.15f).coerceIn(0f, 255f)
            val yellowSignal = ((cYellow - aYellow) * 1.20f + localLumaContrast * 0.15f).coerceIn(0f, 255f)
            val infraredSignal = (localLumaContrast * 1.35f).coerceIn(0f, 255f)

            onSample(
                LedFrameSample(
                    centerLuma = cLuma,
                    redSignal = redSignal,
                    yellowSignal = yellowSignal,
                    infraredSignal = infraredSignal,
                    timestampNs = image.imageInfo.timestamp,
                    displaySignal = DisplayContrast.signal(
                        (centerLumaSum / centerCount).toFloat(), aLuma
                    )
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

    private class TopAverage(private val size: Int) {
        private val values = FloatArray(size)
        var count: Int = 0
            private set

        fun add(value: Float) {
            if (count < size) {
                values[count++] = value
                if (count == size) values.sort()
                return
            }
            if (value <= values[0]) return
            values[0] = value
            values.sort()
        }

        fun average(): Float {
            if (count == 0) return 0f
            var sum = 0f
            for (i in 0 until count) sum += values[i]
            return sum / count
        }
    }

    private data class Rgb(val r: Float, val g: Float, val b: Float)
}

data class LedFrameSample(
    val centerLuma: Float,
    val redSignal: Float,
    val yellowSignal: Float,
    val infraredSignal: Float,
    val timestampNs: Long,
    val displaySignal: Float = 0f,
    val sampledForDisplay: Boolean = false
)

