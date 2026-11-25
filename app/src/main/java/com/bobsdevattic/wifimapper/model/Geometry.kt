package com.bobsdevattic.wifimapper.model

import kotlin.math.sqrt

/**
 * A point in the ARCore *local* coordinate frame, in metres.
 *
 * ARCore's convention: +X right, +Y up (against gravity), -Z forward from the
 * camera's start pose. The origin is wherever the AR session was created, so
 * these coordinates are only meaningful relative to that session's [GeoAnchor].
 */
data class Vector3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vector3) = Vector3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vector3) = Vector3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vector3(x * s, y * s, z * s)
    fun length() = sqrt(x * x + y * y + z * z)
    fun distanceTo(o: Vector3) = (this - o).length()

    companion object { val ZERO = Vector3(0f, 0f, 0f) }
}

/**
 * The real-world fix captured at (or near) the AR session origin. Lets us place
 * an otherwise-local AR point cloud onto the globe and align separate sessions.
 *
 * @param latitude degrees
 * @param longitude degrees
 * @param altitudeMeters GPS ellipsoidal altitude (coarse: ±10–20 m typical)
 * @param accuracyMeters horizontal accuracy radius reported by the fix
 * @param headingDegrees device heading (0=N) at anchor time, used to rotate the
 *        AR local frame into a world ENU frame. Null if unknown.
 */
data class GeoAnchor(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val accuracyMeters: Float,
    val headingDegrees: Float?,
)
