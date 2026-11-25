package com.bobsdevattic.wifimapper.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/** Tracker radar timing and sound helpers. Pure Kotlin. */
object TrackerMath {

    const val MIN_M = 0.5
    const val MAX_M = 40.0

    // --- Zoom ---------------------------------------------------------------
    /** Closest zoom: the outer edge of the fan is 3 ft away. */
    const val MIN_OUTER_M = 0.9144
    /** Default view and where grid fading starts: 100 ft. */
    const val DEFAULT_OUTER_M = 30.48
    /** Furthest zoom (~330 ft); the grid is almost faded out here. */
    const val MAX_OUTER_M = 100.0
    /** The scale spans outer/80 .. outer (same 80:1 span as the original 0.5–40 m). */
    private const val SPAN = 80.0

    /** New outer range after a pinch: spreading fingers (factor > 1) zooms in. */
    fun zoomed(outerM: Double, pinchFactor: Float): Double =
        (outerM / pinchFactor.coerceAtLeast(0.01f)).coerceIn(MIN_OUTER_M, MAX_OUTER_M)

    /**
     * Grid opacity: full up to 100 ft, then fading (log-linearly) to [minAlpha]
     * at the furthest zoom. Beyond ~100 ft signal-based distances are guesswork.
     */
    fun gridAlpha(outerM: Double, minAlpha: Float = 0.1f): Float {
        if (outerM <= DEFAULT_OUTER_M) return 1f
        val t = (log10(outerM / DEFAULT_OUTER_M) / log10(MAX_OUTER_M / DEFAULT_OUTER_M)).toFloat().coerceIn(0f, 1f)
        return 1f - (1f - minAlpha) * t
    }

    /**
     * Where [distanceM] sits on the tracker's log range scale, 0 (emitter) .. 1
     * (outer edge), for a view whose edge is [outerM] away. The ping wave's
     * progress uses the same scale, so the wave reaches a target exactly when
     * its progress equals this value.
     */
    fun rangeFraction(distanceM: Double, outerM: Double = MAX_M): Float {
        val inner = outerM / SPAN
        return ((log10(distanceM.coerceIn(inner, outerM)) - log10(inner)) / log10(SPAN)).toFloat()
    }

    /** True when a wave moving from [prev] to [now] passes [threshold] this frame. */
    fun crossed(prev: Float, now: Float, threshold: Float): Boolean = prev < threshold && now >= threshold

    /** Return-bleep pitch: ~900 Hz far away, ~2 kHz right on top of it. */
    fun blipPitchHz(proximity: Float): Float = 900f + 1100f * proximity.coerceIn(0f, 1f)
}

/** Generates short tones as 16-bit PCM. */
object ToneSynth {
    /**
     * A sine "chirp" from [startHz] to [endHz] over [durationMs], shaped by a
     * raised-cosine envelope so it starts and ends at silence (no clicks).
     */
    fun chirp(startHz: Float, endHz: Float, durationMs: Int, volume: Float, sampleRate: Int = 44_100): ShortArray {
        val n = sampleRate * durationMs / 1000
        val out = ShortArray(n)
        var phase = 0.0
        val amp = volume.coerceIn(0f, 1f) * Short.MAX_VALUE
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = startHz + (endHz - startHz) * t
            phase += 2 * PI * f / sampleRate
            val env = 0.5 * (1 - cos(2 * PI * t))          // 0 → 1 → 0
            out[i] = (amp * env * sin(phase)).toInt().toShort()
        }
        return out
    }
}
