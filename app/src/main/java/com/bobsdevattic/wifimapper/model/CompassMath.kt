package com.bobsdevattic.wifimapper.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Compass helpers for heading-locked displays. Pure Kotlin. */
object CompassMath {

    /** Bearing relative to the way you face, in −180..180 (+ = to your right). */
    fun relative(bearingDeg: Float, headingDeg: Float): Float =
        (((bearingDeg - headingDeg) % 360f) + 540f) % 360f - 180f

    fun normalize(deg: Float): Float = ((deg % 360f) + 360f) % 360f

    /** Compass points to label, with whether each is a cardinal (N/E/S/W). */
    val POINTS: List<Triple<Float, String, Boolean>> = listOf(
        Triple(0f, "N", true), Triple(45f, "NE", false), Triple(90f, "E", true), Triple(135f, "SE", false),
        Triple(180f, "S", true), Triple(225f, "SW", false), Triple(270f, "W", true), Triple(315f, "NW", false),
    )

    /**
     * Smooths a jittery compass heading with an exponential moving average on the
     * unit circle, so 359° → 1° averages to ~0°, not 180°.
     */
    class HeadingSmoother(private val alpha: Float = 0.2f) {
        private var x = 0.0
        private var y = 0.0
        private var initialised = false

        fun add(deg: Float): Float {
            val r = Math.toRadians(deg.toDouble())
            if (!initialised) { x = cos(r); y = sin(r); initialised = true }
            else { x += alpha * (cos(r) - x); y += alpha * (sin(r) - y) }
            return normalize(Math.toDegrees(atan2(y, x)).toFloat())
        }
    }
}
