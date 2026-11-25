package com.bobsdevattic.wifimapper.viz

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Summary of WiFi coverage across a set of sampled points, including a single
 * **uniformity score** describing how even the signal is.
 *
 * @param uniformity 0..100. 100 = every point has the same signal; lower = more
 *        variation (dead spots next to strong spots).
 */
data class CoverageStats(
    val count: Int,
    val strongest: CloudPoint?,
    val weakest: CloudPoint?,
    val meanRssi: Int,
    val stdDevDb: Float,
    val spreadDb: Int,       // strongest - weakest, in dB
    val uniformity: Int,     // 0..100
    val weakZoneCount: Int,  // points at/below POOR_RSSI
) {
    companion object {
        /** Below this (dBm), coverage is considered poor / likely a dead-ish zone. */
        const val POOR_RSSI = -75

        /**
         * Spread (in dB of std-dev) at which uniformity hits 0. A standard deviation
         * of 15 dB across a home is very uneven; 0 dB is perfectly flat.
         */
        const val UNIFORMITY_FULL_SCALE_DB = 15f

        fun from(points: List<CloudPoint>): CoverageStats {
            if (points.isEmpty()) {
                return CoverageStats(0, null, null, 0, 0f, 0, 0, 0)
            }
            val rssis = points.map { it.rssi }
            val mean = rssis.average()
            val variance = rssis.sumOf { (it - mean) * (it - mean) } / rssis.size
            val stdDev = sqrt(variance).toFloat()

            val strongest = points.maxByOrNull { it.rssi }
            val weakest = points.minByOrNull { it.rssi }
            val spread = (strongest!!.rssi - weakest!!.rssi)

            // Uniformity: inverse of how much the signal varies, clamped to 0..100.
            val uniformity = (100f * (1f - (stdDev / UNIFORMITY_FULL_SCALE_DB)))
                .coerceIn(0f, 100f)
                .roundToInt()

            return CoverageStats(
                count = points.size,
                strongest = strongest,
                weakest = weakest,
                meanRssi = mean.roundToInt(),
                stdDevDb = stdDev,
                spreadDb = spread,
                uniformity = uniformity,
                weakZoneCount = rssis.count { it <= POOR_RSSI },
            )
        }
    }

    /** Qualitative label for the uniformity score. */
    val uniformityLabel: String
        get() = when {
            count < 2 -> "—"
            uniformity >= 80 -> "Very even"
            uniformity >= 60 -> "Even"
            uniformity >= 40 -> "Patchy"
            uniformity >= 20 -> "Uneven"
            else -> "Very uneven"
        }
}
