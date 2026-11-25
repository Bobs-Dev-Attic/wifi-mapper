package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassMathTest {

    @Test fun relativeBearingWrapsAroundNorth() {
        assertEquals(20f, CompassMath.relative(10f, 350f), 1e-4f)   // north-east of a NNW heading: to the right
        assertEquals(-20f, CompassMath.relative(350f, 10f), 1e-4f)
        assertEquals(-30f, CompassMath.relative(0f, 30f), 1e-4f)    // facing 30°: north is 30° to the left
        assertEquals(-180f, CompassMath.relative(180f, 0f), 1e-4f)  // directly behind
    }

    @Test fun smootherAveragesAcrossNorthNotThroughSouth() {
        val s = CompassMath.HeadingSmoother(alpha = 0.5f)
        var out = 0f
        repeat(20) { out = s.add(if (it % 2 == 0) 350f else 10f) }
        val distFromNorth = minOf(out, 360f - out)
        assertTrue("got $out", distFromNorth < 6f)
    }

    @Test fun smootherDampsJitterButFollowsTurns() {
        val s = CompassMath.HeadingSmoother(alpha = 0.2f)
        repeat(30) { s.add(90f) }
        val spike = s.add(120f)                  // one 30° glitch moves it only a little
        assertTrue("spike=$spike", spike in 90f..97f)
        var out = 0f
        repeat(40) { out = s.add(180f) }         // a real turn is followed
        assertEquals(180f, out, 1f)
    }

    @Test fun fourCardinals() {
        assertEquals(listOf("N", "E", "S", "W"), CompassMath.POINTS.filter { it.third }.map { it.second })
    }
}
