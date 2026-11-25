package com.bobsdevattic.wifimapper.model

import kotlin.math.sqrt

/**
 * A wall-like vertical surface detected by ARCore, modelled as an upright
 * rectangle in the AR-local frame.
 *
 * @param center rectangle centre (metres)
 * @param nx,nz horizontal unit normal (the way the surface faces)
 * @param width horizontal extent along the surface (metres)
 * @param height vertical extent (metres)
 */
data class WallObservation(
    val center: Vector3,
    val nx: Float,
    val nz: Float,
    val width: Float,
    val height: Float,
) {
    /** Horizontal unit vector running along the wall surface. */
    val tangent: Vector3 get() = Vector3(-nz, 0f, nx)

    /** Four corners, for drawing: bottom-left, bottom-right, top-right, top-left. */
    fun corners(): List<Vector3> {
        val t = tangent * (width / 2f)
        val up = Vector3(0f, height / 2f, 0f)
        return listOf(center - t - up, center + t - up, center + t + up, center - t + up)
    }

    /** Signed distance of [p] from the wall's plane (positive on the normal side). */
    fun signedDistance(p: Vector3): Float =
        (p.x - center.x) * nx + (p.z - center.z) * nz

    /**
     * True if the straight segment a→b passes *through* this wall's rectangle
     * (not just its infinite plane).
     */
    fun intersects(a: Vector3, b: Vector3): Boolean {
        val da = signedDistance(a)
        val db = signedDistance(b)
        if (da * db >= 0f) return false            // same side (or touching)
        val s = da / (da - db)
        val p = a + (b - a) * s
        val along = (p.x - center.x) * tangent.x + (p.z - center.z) * tangent.z
        return kotlin.math.abs(along) <= width / 2f &&
            kotlin.math.abs(p.y - center.y) <= height / 2f
    }

    companion object {
        /** Build from a (possibly non-normalised) normal; returns null if it's degenerate. */
        fun of(center: Vector3, normalX: Float, normalZ: Float, width: Float, height: Float): WallObservation? {
            val len = sqrt(normalX * normalX + normalZ * normalZ)
            if (len < 1e-3f) return null
            return WallObservation(center, normalX / len, normalZ / len, width, height)
        }
    }
}
