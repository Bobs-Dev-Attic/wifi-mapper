package com.bobsdevattic.wifimapper.model

/**
 * Projects AR world-space points onto the screen using ARCore's camera view and
 * projection matrices (OpenGL convention: column-major float[16]). Lets us draw
 * an overlay with plain 2D graphics on top of the camera feed. Pure Kotlin.
 */
object ArProjector {

    /** A projected point: screen pixels plus its distance from the camera (m). */
    data class Screen(val x: Float, val y: Float, val distanceM: Float)

    /** out = m (4x4, column-major) × v (vec4). */
    fun mulMV(m: FloatArray, v: FloatArray): FloatArray {
        val out = FloatArray(4)
        for (row in 0 until 4) {
            out[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3]
        }
        return out
    }

    /**
     * Screen position of [p], or null if it's behind the camera or well outside
     * the view. [margin] keeps points slightly off-screen so big orbs don't pop.
     */
    fun project(
        view: FloatArray,
        projection: FloatArray,
        p: Vector3,
        widthPx: Float,
        heightPx: Float,
        margin: Float = 0.15f,
    ): Screen? {
        val eye = mulMV(view, floatArrayOf(p.x, p.y, p.z, 1f))
        // In eye space the camera looks down −Z; anything with z ≥ −near is behind/at it.
        if (eye[2] > -0.05f) return null
        val clip = mulMV(projection, eye)
        val w = clip[3]
        if (w <= 0f) return null
        val nx = clip[0] / w
        val ny = clip[1] / w
        if (nx < -1 - margin || nx > 1 + margin || ny < -1 - margin || ny > 1 + margin) return null
        val dist = kotlin.math.sqrt(eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2])
        return Screen(
            x = (nx + 1f) / 2f * widthPx,
            y = (1f - ny) / 2f * heightPx,      // NDC +y is up; screen +y is down
            distanceM = dist,
        )
    }

    /** On-screen orb radius (px): big when near, small when far, clamped. */
    fun orbRadiusPx(distanceM: Float, sizeAt1m: Float = 46f): Float =
        (sizeAt1m / distanceM.coerceAtLeast(0.2f)).coerceIn(5f, 90f)
}
