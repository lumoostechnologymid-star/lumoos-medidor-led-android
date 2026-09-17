package com.lumoos.medidorled.camera

import kotlin.math.max
import kotlin.math.min

/** Chromatic contrast: neutral sunlight is zero, yellow/green pulses stay visible.
 * Normalize exposure without amplifying near-black sensor noise.
 */
object LedColorSignal {
    fun yellowGreen(r: Float, g: Float, b: Float): Float {
        val color = (min(g, r * 2f) - b).coerceAtLeast(0f)
        return (255f * color / max(32f, max(r, max(g, b)))).coerceIn(0f, 255f)
    }
}
