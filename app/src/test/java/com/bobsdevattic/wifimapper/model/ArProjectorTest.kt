package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class ArProjectorTest {

    /** Same layout as android.opengl.Matrix.perspectiveM (column-major). */
    private fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(Math.toRadians(fovYDeg / 2.0)).toFloat()
        val m = FloatArray(16)
        m[0] = f / aspect; m[5] = f
        m[10] = (far + near) / (near - far); m[11] = -1f
        m[14] = 2f * far * near / (near - far)
        return m
    }

    private val identity = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    /** View matrix for a camera at (cx, 0, 0) looking down −Z: translate by −cx. */
    private fun cameraAtX(cx: Float) = identity.copyOf().also { it[12] = -cx }

    private val proj = perspective(60f, 1080f / 2340f, 0.05f, 100f)

    @Test fun pointStraightAheadIsCentred() {
        val s = ArProjector.project(identity, proj, Vector3(0f, 0f, -2f), 1080f, 2340f)!!
        assertEquals(540f, s.x, 0.5f)
        assertEquals(1170f, s.y, 0.5f)
        assertEquals(2f, s.distanceM, 1e-4f)
    }

    @Test fun rightIsRightAndUpIsUp() {
        val right = ArProjector.project(identity, proj, Vector3(0.3f, 0f, -2f), 1080f, 2340f)!!
        val up = ArProjector.project(identity, proj, Vector3(0f, 0.3f, -2f), 1080f, 2340f)!!
        assertTrue(right.x > 540f)
        assertTrue("screen y grows downward", up.y < 1170f)
    }

    @Test fun behindCameraIsHidden() {
        assertNull(ArProjector.project(identity, proj, Vector3(0f, 0f, 1f), 1080f, 2340f))
    }

    @Test fun farOffToTheSideIsHidden() {
        assertNull(ArProjector.project(identity, proj, Vector3(50f, 0f, -1f), 1080f, 2340f))
    }

    @Test fun cameraMovementShiftsPoints() {
        // Camera moves +0.3 m in x: a fixed point now appears LEFT of centre.
        // (Portrait's horizontal FOV is narrow: a 1 m shift at 3 m is off-screen.)
        val s = ArProjector.project(cameraAtX(0.3f), proj, Vector3(0f, 0f, -3f), 1080f, 2340f)
        assertNull(ArProjector.project(cameraAtX(1f), proj, Vector3(0f, 0f, -3f), 1080f, 2340f))
        assertNotNull(s)
        assertTrue(s!!.x < 540f)
    }

    @Test fun orbsShrinkWithDistance() {
        assertTrue(ArProjector.orbRadiusPx(0.5f) > ArProjector.orbRadiusPx(3f))
        assertEquals(90f, ArProjector.orbRadiusPx(0.01f))
        assertEquals(5f, ArProjector.orbRadiusPx(100f))
    }
}
