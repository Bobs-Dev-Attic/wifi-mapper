package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrackerMathTest {

    @Test fun rangeFractionIsLogScaledAndClamped() {
        assertEquals(0f, TrackerMath.rangeFraction(0.1), 1e-6f)
        assertEquals(1f, TrackerMath.rangeFraction(100.0), 1e-6f)
        val near = TrackerMath.rangeFraction(1.0); val far = TrackerMath.rangeFraction(10.0)
        assertTrue(near < far)
        // Log scale: 1→10 m spans the same as 0.5→5 m.
        assertEquals(TrackerMath.rangeFraction(5.0) - TrackerMath.rangeFraction(0.5), far - near, 1e-5f)
    }

    @Test fun crossingFiresOncePerPass() {
        val t = 0.4f
        val frames = listOf(0f, 0.1f, 0.3f, 0.45f, 0.7f, 1f, 0f, 0.2f, 0.41f)  // two wave cycles
        val hits = frames.zipWithNext().count { (a, b) -> TrackerMath.crossed(a, b, t) }
        assertEquals(2, hits)
        assertFalse(TrackerMath.crossed(1f, 0f, t))                           // reset isn't a hit
    }

    @Test fun pitchRisesWithProximity() {
        assertEquals(900f, TrackerMath.blipPitchHz(0f))
        assertEquals(2000f, TrackerMath.blipPitchHz(1f))
        assertTrue(TrackerMath.blipPitchHz(0.7f) > TrackerMath.blipPitchHz(0.3f))
    }

    @Test fun chirpHasRightLengthSilentEdgesAndPitch() {
        val sr = 44_100
        val s = ToneSynth.chirp(1000f, 1000f, durationMs = 100, volume = 0.5f, sampleRate = sr)
        assertEquals(sr / 10, s.size)
        assertTrue("starts silent", abs(s.first().toInt()) < 50)
        assertTrue("ends silent", abs(s.last().toInt()) < 400)
        assertTrue("respects volume", s.maxOf { abs(it.toInt()) } <= (0.5f * Short.MAX_VALUE).toInt() + 1)
        // ~1000 Hz for 0.1 s ≈ 200 zero crossings.
        val crossings = s.toList().zipWithNext().count { (a, b) -> (a < 0) != (b < 0) }
        assertTrue("crossings=$crossings", crossings in 190..210)
    }
}
