package com.bobsdevattic.wifimapper.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Builds a direction estimate from a "body-shadow sweep": the user turns slowly
 * in a circle holding the phone in front of them. Their body attenuates signal
 * arriving from behind by roughly 5–10 dB, so RSSI peaks when they FACE the
 * source. We bin samples by compass heading and find the strongest direction.
 *
 * Pure Kotlin (no Android) so it's unit-testable.
 */
class RadarAccumulator(val sectorCount: Int = 36) {

    private val sum = DoubleArray(sectorCount)
    private val count = IntArray(sectorCount)

    val sectorWidthDeg: Float get() = 360f / sectorCount

    fun reset() { sum.fill(0.0); count.fill(0) }

    fun add(headingDeg: Float, rssi: Int) {
        val i = sectorOf(headingDeg)
        sum[i] += rssi; count[i]++
    }

    fun sectorOf(headingDeg: Float): Int {
        val h = ((headingDeg % 360f) + 360f) % 360f
        return (h / sectorWidthDeg).toInt().coerceIn(0, sectorCount - 1)
    }

    /** Mean RSSI per sector, or null where no samples yet. */
    fun sectorMeans(): List<Float?> =
        (0 until sectorCount).map { if (count[it] == 0) null else (sum[it] / count[it]).toFloat() }

    /** Fraction of the circle that has at least one sample (0..1). */
    fun coverage(): Float = count.count { it > 0 }.toFloat() / sectorCount

    /**
     * Estimated bearing (degrees, 0 = north, clockwise) of the strongest signal,
     * or null until enough of the circle is covered.
     *
     * The body shadow makes RSSI vary roughly like a + b·cos(θ − φ) around the
     * circle; the peak is broad (only ~0.5 dB down at ±30°), so picking the single
     * strongest sector is noise-dominated. Instead we fit that cosine using ALL
     * sectors: φ = atan2(Σ(mᵢ−m̄)·sin θᵢ, Σ(mᵢ−m̄)·cos θᵢ) — the first circular
     * harmonic, i.e. the least-squares direction of the shadow.
     */
    fun strongestBearing(minCoverage: Float = 0.6f): Float? {
        if (coverage() < minCoverage) return null
        val filled = sectorMeans().withIndex().filter { it.value != null }
        val mean = filled.map { it.value!!.toDouble() }.average()
        var x = 0.0; var y = 0.0
        for ((i, m) in filled) {
            val theta = Math.toRadians((i + 0.5) * sectorWidthDeg.toDouble())
            x += (m!! - mean) * sin(theta)
            y += (m - mean) * cos(theta)
        }
        if (x == 0.0 && y == 0.0) return null      // perfectly flat: no direction
        val deg = Math.toDegrees(atan2(x, y)).toFloat()
        return (deg + 360f) % 360f
    }

    /** Strongest-minus-weakest sector difference (dB): a large value means a clear direction. */
    fun contrastDb(): Float {
        val m = sectorMeans().filterNotNull()
        return if (m.size < 2) 0f else m.max() - m.min()
    }

    companion object {
        private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        fun compassPoint(deg: Float): String = POINTS[(((deg % 360f) + 360f) % 360f / 45f).roundToInt() % 8]
    }
}

/** "Warmer / colder" from a stream of RSSI samples while walking. */
class TrendDetector(private val windowMs: Long = 2_000, private val thresholdDb: Float = 3f) {

    enum class Trend { WARMER, COLDER, STEADY, UNKNOWN }

    private val samples = ArrayDeque<Pair<Long, Int>>()

    fun add(timeMs: Long, rssi: Int) {
        samples.addLast(timeMs to rssi)
        while (samples.isNotEmpty() && samples.first().first < timeMs - 3 * windowMs) samples.removeFirst()
    }

    /** Compares the most recent window's mean with the one(s) before it. */
    fun trend(nowMs: Long): Trend {
        val recent = samples.filter { it.first >= nowMs - windowMs }.map { it.second }
        val before = samples.filter { it.first < nowMs - windowMs }.map { it.second }
        if (recent.size < 2 || before.size < 2) return Trend.UNKNOWN
        val delta = recent.average() - before.average()
        return when {
            delta >= thresholdDb -> Trend.WARMER
            delta <= -thresholdDb -> Trend.COLDER
            else -> Trend.STEADY
        }
    }
}
