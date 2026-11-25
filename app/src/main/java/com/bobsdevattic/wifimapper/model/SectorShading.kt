package com.bobsdevattic.wifimapper.model

/**
 * Shading for the radar's sweep "returns". The strongest direction is singled
 * out as the peak; every other direction is graded relative to the runner-up
 * (next strongest = 1, weakest = 0), so the gradient shows the spread of the
 * remaining signals instead of being flattened by one dominant peak.
 */
object SectorShading {
    data class Shade(val level: Float, val peak: Boolean)

    /**
     * @param means mean RSSI per sector (null = not swept yet).
     * @param peakIndex sector to treat as the peak (e.g. the one holding the
     *   fitted bearing); defaults to the strongest sector.
     * @return one entry per sector, null where [means] is null.
     */
    fun shade(means: List<Float?>, peakIndex: Int? = null): List<Shade?> {
        val filled = means.withIndex().filter { it.value != null }
        if (filled.isEmpty()) return means.map { null }
        val peak = peakIndex?.takeIf { means.getOrNull(it) != null }
            ?: filled.maxBy { it.value!! }.index
        val others = filled.filter { it.index != peak }.map { it.value!! }
        val lo = others.minOrNull() ?: 0f
        val runnerUp = others.maxOrNull() ?: 0f
        val span = runnerUp - lo
        return means.mapIndexed { i, m ->
            when {
                m == null -> null
                i == peak -> Shade(1f, peak = true)
                span <= 0f -> Shade(0.5f, peak = false)          // all the others are equal
                else -> Shade(((m - lo) / span).coerceIn(0f, 1f), peak = false)
            }
        }
    }

    /** Sector index holding [bearingDeg] for [sectorCount] equal sectors from north. */
    fun sectorOf(bearingDeg: Float, sectorCount: Int): Int {
        val h = ((bearingDeg % 360f) + 360f) % 360f
        return (h / (360f / sectorCount)).toInt().coerceIn(0, sectorCount - 1)
    }
}
