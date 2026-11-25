package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorShadingTest {
    @Test fun peakHighlightedOthersGradedAgainstRunnerUp() {
        val s = SectorShading.shade(listOf(-60f, -50f, -55f, -70f, null))
        assertTrue(s[1]!!.peak); assertEquals(1f, s[1]!!.level)
        assertFalse(s[2]!!.peak); assertEquals(1f, s[2]!!.level, 1e-6f)     // runner-up tops the gradient
        assertEquals(0f, s[3]!!.level, 1e-6f)                                // weakest
        assertEquals(10f / 15f, s[0]!!.level, 1e-5f)
        assertNull(s[4])
        assertEquals(1, s.count { it?.peak == true })
    }

    @Test fun dominantPeakDoesNotFlattenTheRest() {
        // A 30 dB peak would squash the others to ~0 on a min..max scale.
        val s = SectorShading.shade(listOf(-40f, -70f, -72f, -74f))
        assertEquals(1f, s[1]!!.level, 1e-6f)
        assertEquals(0.5f, s[2]!!.level, 1e-6f)
    }

    @Test fun peakIndexOverrideAndEdgeCases() {
        val s = SectorShading.shade(listOf(-50f, -51f, -60f), peakIndex = 1)
        assertTrue(s[1]!!.peak); assertFalse(s[0]!!.peak)
        assertEquals(1f, s[0]!!.level, 1e-6f)
        // Override pointing at an unswept sector falls back to the strongest.
        assertTrue(SectorShading.shade(listOf(-50f, null, -60f), peakIndex = 1)[0]!!.peak)
        assertTrue(SectorShading.shade(listOf(null, null)).all { it == null })
        assertTrue(SectorShading.shade(listOf(-50f))[0]!!.peak)
        assertEquals(0.5f, SectorShading.shade(listOf(-40f, -60f, -60f))[1]!!.level)
    }

    @Test fun sectorOfBearing() {
        assertEquals(0, SectorShading.sectorOf(5f, 36))
        assertEquals(35, SectorShading.sectorOf(-5f, 36))
        assertEquals(9, SectorShading.sectorOf(90f, 36))
        assertEquals(0, SectorShading.sectorOf(360f, 36))
    }
}
