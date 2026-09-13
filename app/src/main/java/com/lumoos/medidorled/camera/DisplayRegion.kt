package com.lumoos.medidorled.camera

/** Shared proportions for the displayed square and the analysis crop. */
object DisplayRegion {
    const val CENTER_FRACTION = 0.08f
    const val OUTER_FRACTION = 0.18f

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom
    }
    data class Regions(val center: Bounds, val outer: Bounds)
    data class Reading(val centerLuma: Float, val signal: Float)

    fun regions(left: Int, top: Int, right: Int, bottom: Int): Regions {
        val shortest = minOf(right - left, bottom - top)
        require(shortest >= 8)
        val centerSide = (shortest * CENTER_FRACTION).toInt().coerceAtLeast(2)
        val outerSide = (shortest * OUTER_FRACTION).toInt().coerceAtLeast(centerSide + 2).coerceAtMost(shortest)
        fun square(side: Int): Bounds {
            val x = left + (right - left - side) / 2
            val y = top + (bottom - top - side) / 2
            return Bounds(x, y, x + side, y + side)
        }
        return Regions(square(centerSide), square(outerSide))
    }

    fun measure(regions: Regions, pixel: (Int, Int) -> Int?): Reading? {
        var centerSum = 0.0
        var outerSum = 0.0
        var centerCount = 0
        var outerCount = 0
        // The display region is small; use every pixel to avoid skipping thin segments.
        for (y in regions.outer.top until regions.outer.bottom) {
            for (x in regions.outer.left until regions.outer.right) {
                val value = pixel(x, y) ?: continue
                if (regions.center.contains(x, y)) {
                    centerSum += value; centerCount++
                } else {
                    outerSum += value; outerCount++
                }
            }
        }
        if (centerCount == 0 || outerCount == 0) return null
        val center = (centerSum / centerCount).toFloat()
        return Reading(center, DisplayContrast.signal(center, (outerSum / outerCount).toFloat()))
    }
}
