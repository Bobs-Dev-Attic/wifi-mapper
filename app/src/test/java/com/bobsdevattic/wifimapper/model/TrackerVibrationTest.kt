package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerVibrationTest {
    @Test fun buzzGetsLongerAndStrongerUpClose() {
        assertEquals(20L, TrackerMath.blipVibrationMs(0f))
        assertEquals(60L, TrackerMath.blipVibrationMs(1f))
        assertTrue(TrackerMath.blipVibrationMs(0.5f) in 21L..59L)
        assertEquals(70, TrackerMath.blipVibrationAmplitude(0f))
        assertEquals(255, TrackerMath.blipVibrationAmplitude(1f))
        // Out-of-range proximity is clamped to a valid amplitude.
        assertEquals(255, TrackerMath.blipVibrationAmplitude(3f))
        assertEquals(70, TrackerMath.blipVibrationAmplitude(-1f))
    }
}
