package com.bobsdevattic.wifimapper.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/** Tracker radar timing and sound helpers. Pure Kotlin. */
object TrackerMath {

    const val MIN_M = 0.5
    const val MAX_M = 40.0

    /**
     * Where [distanceM] sits on the tracker's log range scale, 0 (emitter) .. 1
     * (outer edge). The ping wave's progress uses the same scale, so the wave
     * reaches a target exactly when its progress equals this value.
     */
    fun rangeFraction(distanceM: Double): Float =
        ((log10(distanceM.coerceIn(MIN_M, MAX_M)) - log10(MIN_M)) / (log10(MAX_M) - log10(MIN_M))).toFloat()

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
