package com.lumoos.medidorled.camera

/** Positive signal when the selected center is darker than the surrounding LCD. */
object DisplayContrast {
    fun signal(centerLuma: Float, ambientLuma: Float): Float =
        (ambientLuma - centerLuma).coerceIn(0f, 255f)
}
