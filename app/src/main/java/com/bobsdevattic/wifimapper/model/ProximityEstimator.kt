package com.bobsdevattic.wifimapper.model

/**
 * Turns a noisy stream of RSSI readings from one device into a steady
 * "how close am I?" signal for a hot/cold finder.
 *
 * BLE RSSI jumps 5–10 dB between consecutive packets (multipath, body
 * blocking, antenna orientation). We take a median of the last few readings
 * (kills single spikes), then an exponential moving average (smooths the rest).
 * Pure Kotlin — unit-testable.
 */
class ProximityEstimator(
    private val medianWindow: Int = 5,
    private val alpha: Float = 0.3f,
    private val farRssi: Int = -95,
    private val nearRssi: Int = -40,
) {
    enum class Band(val label: String) {
        FAR("Far"), CLOSER("Getting closer"), NEAR("Near"), VERY_CLOSE("Very close"), HERE("Right here")
    }

    private val recent = ArrayDeque<Int>()
    var smoothed: Float? = null; private set
    var peak: Float? = null; private set
    var lastHeardMs: Long? = null; private set
    var samples: Int = 0; private set

    fun reset() { recent.clear(); smoothed = null; peak = null; lastHeardMs = null; samples = 0 }

    fun add(rssi: Int, nowMs: Long) {
        recent.addLast(rssi)
        while (recent.size > medianWindow) recent.removeFirst()
        val med = recent.sorted()[recent.size / 2].toFloat()
        smoothed = smoothed?.let { it + alpha * (med - it) } ?: med
        if (recent.size >= 3) peak = maxOf(peak ?: smoothed!!, smoothed!!)   // ignore warm-up
        lastHeardMs = nowMs
        samples++
    }

    /** 0 (far / not heard) .. 1 (right next to it). */
    fun level(): Float {
        val s = smoothed ?: return 0f
        return ((s - farRssi) / (nearRssi - farRssi)).coerceIn(0f, 1f)
    }

    fun band(): Band = when (level()) {
        in 0.85f..1f -> Band.HERE
        in 0.65f..0.85f -> Band.VERY_CLOSE
        in 0.45f..0.65f -> Band.NEAR
        in 0.25f..0.45f -> Band.CLOSER
        else -> Band.FAR
    }

    /** Geiger-counter cadence: slow far away, fast when close. */
    fun beepIntervalMs(): Long {
        val l = level()
        val curved = l * l                       // stays slow until you're genuinely near
        return (1600 - curved * (1600 - 120)).toLong()
    }

    /** True if the current reading is well below the best seen: you may have walked past it. */
    fun belowPeak(thresholdDb: Float = 8f): Boolean {
        val s = smoothed ?: return false
        val p = peak ?: return false
        return p - s >= thresholdDb
    }

    fun secondsSinceHeard(nowMs: Long): Long? = lastHeardMs?.let { (nowMs - it) / 1000 }
}
