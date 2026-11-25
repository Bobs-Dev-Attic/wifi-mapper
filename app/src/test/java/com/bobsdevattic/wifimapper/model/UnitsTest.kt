package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    private val M = DistanceUnit.METRIC
    private val F = DistanceUnit.IMPERIAL

    @Test fun defaultFollowsRegion() {
        assertEquals(F, DistanceUnit.forCountry("US"))
        assertEquals(F, DistanceUnit.forCountry("us"))
        assertEquals(M, DistanceUnit.forCountry("GB"))
        assertEquals(M, DistanceUnit.forCountry("DE"))
    }

    @Test fun distances() {
        assertEquals("<1 m", UnitFormat.distance(0.5, M))
        assertEquals("4.2 m", UnitFormat.distance(4.2, M))
        assertEquals("14 ft", UnitFormat.distance(4.2, F))      // 13.78 → 14
        assertEquals("<3 ft", UnitFormat.distance(0.5, F))
        assertEquals(">330 ft", UnitFormat.distance(150.0, F))
    }

    @Test fun rangesMatchTheMetricOriginal() {
        assertEquals("5.9-22 m", UnitFormat.range(5.9 to 22.0, M))
        assertEquals("19-72 ft", UnitFormat.range(5.9 to 22.0, F))
        // SignalDetails.formatRange keeps its metric behaviour.
        assertEquals(UnitFormat.range(5.9 to 22.0, M), SignalDetails.formatRange(5.9 to 22.0))
    }

    @Test fun lengths() {
        assertEquals("3.0 m", UnitFormat.length(3.0, M))
        assertEquals("9.8 ft", UnitFormat.length(3.0, F))
    }

    @Test fun trackerRingsAreRoundNumbersInEachUnit() {
        assertEquals(listOf("1m", "3m", "10m", "30m"), UnitFormat.trackerRings(M).map { it.second })
        val ft = UnitFormat.trackerRings(F)
        assertEquals(listOf("3ft", "10ft", "30ft", "100ft"), ft.map { it.second })
        assertEquals(30.48, ft.last().first, 0.01)               // 100 ft in metres
        // All rings fit inside the tracker's 0.5–40 m scale.
        assertTrue((UnitFormat.trackerRings(M) + ft).all { it.first in TrackerMath.MIN_M..TrackerMath.MAX_M })
    }
}
