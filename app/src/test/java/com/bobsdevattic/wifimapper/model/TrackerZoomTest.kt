package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerZoomTest {
    private val T = TrackerMath

    @Test fun pinchZoomsAndClampsBetween3And330Feet() {
        assertEquals(T.DEFAULT_OUTER_M / 2, T.zoomed(T.DEFAULT_OUTER_M, 2f), 1e-9)     // spread: zoom in
        assertEquals(T.DEFAULT_OUTER_M * 2, T.zoomed(T.DEFAULT_OUTER_M, 0.5f), 1e-9)   // pinch: zoom out
        assertEquals(T.MIN_OUTER_M, T.zoomed(1.0, 100f), 1e-9)                           // 3 ft floor
        assertEquals(T.MAX_OUTER_M, T.zoomed(50.0, 0.01f), 1e-9)                         // ~330 ft ceiling
        assertEquals(3.0, UnitFormat.toFeet(T.MIN_OUTER_M), 0.01)
        assertEquals(100.0, UnitFormat.toFeet(T.DEFAULT_OUTER_M), 0.01)
    }

    @Test fun gridFullUpTo100FeetThenFades() {
        assertEquals(1f, T.gridAlpha(T.MIN_OUTER_M))
        assertEquals(1f, T.gridAlpha(T.DEFAULT_OUTER_M))
        val mid = T.gridAlpha(55.0)
        assertTrue("mid=$mid", mid < 1f && mid > 0.1f)
        assertTrue(T.gridAlpha(80.0) < mid)                       // keeps fading as you zoom out
        assertEquals(0.1f, T.gridAlpha(T.MAX_OUTER_M), 1e-4f)
    }

    @Test fun rangeFractionFollowsZoom() {
        // A target 10 ft away sits further out when zoomed in.
        val tenFt = 10 / UnitFormat.FT_PER_M
        assertTrue(T.rangeFraction(tenFt, 5.0) > T.rangeFraction(tenFt, T.DEFAULT_OUTER_M))
        assertEquals(1f, T.rangeFraction(500.0, T.DEFAULT_OUTER_M), 1e-6f)  // beyond the view: at the edge
        assertEquals(1f, T.rangeFraction(T.DEFAULT_OUTER_M, T.DEFAULT_OUTER_M), 1e-6f)
        // Default-outer API unchanged for existing callers.
        assertEquals(T.rangeFraction(3.0), T.rangeFraction(3.0, T.MAX_M), 1e-6f)
    }

    @Test fun ringsAdaptToZoomWithRoundNumbers() {
        val ft = DistanceUnit.IMPERIAL; val m = DistanceUnit.METRIC
        assertEquals(listOf("3ft", "10ft", "30ft", "100ft"), UnitFormat.trackerRings(ft, T.DEFAULT_OUTER_M).map { it.second })
        assertEquals(listOf("1m", "3m", "10m", "30m"), UnitFormat.trackerRings(m, T.DEFAULT_OUTER_M).map { it.second })
        // Zoomed right in (3 ft view): only the small rings.
        assertEquals(listOf("1ft", "3ft"), UnitFormat.trackerRings(ft, T.MIN_OUTER_M).map { it.second })
        // Zoomed out: bigger rings appear, tiny ones drop off.
        val far = UnitFormat.trackerRings(ft, T.MAX_OUTER_M).map { it.second }
        assertTrue(far.toString(), "300ft" in far && "3ft" !in far)
    }

    @Test fun viewRangeReadout() {
        assertEquals("100 ft", UnitFormat.viewRange(T.DEFAULT_OUTER_M, DistanceUnit.IMPERIAL))
        assertEquals("30 m", UnitFormat.viewRange(T.DEFAULT_OUTER_M, DistanceUnit.METRIC))
        assertEquals("0.9 m", UnitFormat.viewRange(T.MIN_OUTER_M, DistanceUnit.METRIC))
    }
}
