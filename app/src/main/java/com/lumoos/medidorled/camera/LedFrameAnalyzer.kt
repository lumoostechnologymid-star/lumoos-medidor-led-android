package com.lumoos.medidorled.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Reads the Y (luma) plane from a small center region of the camera image.
 * No image leaves the phone and no frame is stored.
 */
class LedFrameAnalyzer(
    private val roiFraction: Float = 0.16f,
    private val onSample: (luma: Float, timestampNs: Long) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val width = image.width
            val height = image.height

            val roiWidth = (width * roiFraction).toInt().coerceAtLeast(8)
            val roiHeight = (height * roiFraction).toInt().coerceAtLeast(8)
            val left = ((width - roiWidth) / 2).coerceAtLeast(0)
            val top = ((height - roiHeight) / 2).coerceAtLeast(0)
            val right = (left + roiWidth).coerceAtMost(width)
            val bottom = (top + roiHeight).coerceAtMost(height)

            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val limit = buffer.limit()

            var sum = 0L
            var count = 0
            val sampleStep = 2

            var y = top
            while (y < bottom) {
                val rowOffset = y * rowStride
                var x = left
                while (x < right) {
                    val index = rowOffset + x * pixelStride
                    if (index in 0 until limit) {
                        sum += buffer.get(index).toInt() and 0xFF
                        count++
                    }
                    x += sampleStep
                }
                y += sampleStep
            }

            if (count > 0) {
                onSample(sum.toFloat() / count, image.imageInfo.timestamp)
            }
        } finally {
            image.close()
        }
    }
}
