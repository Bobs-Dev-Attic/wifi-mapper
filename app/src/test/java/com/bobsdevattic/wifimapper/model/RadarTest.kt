package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

class RadarTest {

    /** Angular difference in degrees, 0..180. */
    private fun angDiff(a: Float, b: Float): Float {
        val d = abs(((a - b) % 360f + 540f) % 360f - 180f)
        return d
    }

    /** Body-shadow model: up to [shadowDb] weaker when facing away, plus noise. */
    private fun sweep(trueBearing: Float, shadowDb: Double = 8.0, noiseDb: Double = 3.0, seed: Int = 1): RadarAccumulator {
        val r = Random(seed)
        val acc = RadarAccumulator()
        var h = 0f
        while (h < 720f) {   // two slow turns
            val rad = Math.toRadians((h - trueBearing).toDouble())
            val rssi = -55 - shadowDb * (1 - cos(rad)) / 2 + r.nextDouble(-noiseDb, noiseDb)
            acc.add(h % 360f, rssi.toInt())
            h += 4f
        }
        return acc
    }

    @Test fun findsDirectionWithinTwentyDegrees() {
        for ((bearing, seed) in listOf(0f to 1, 45f to 2, 137f to 3, 260f to 4, 350f to 5)) {
            val est = sweep(bearing, seed = seed).strongestBearing()!!
            assertTrue("true=$bearing est=$est", angDiff(est, bearing) <= 20f)
        }
    }

    @Test fun robustAcrossManyRandomSweeps() {
        // 50 random directions/noise seeds: all within 20°, median well under 10°.
        val errors = (1..50).map { seed ->
            val bearing = Random(seed * 7).nextFloat() * 360f
            angDiff(sweep(bearing, seed = seed).strongestBearing()!!, bearing)
        }.sorted()
        assertTrue("max error ${errors.last()}", errors.last() <= 20f)
        assertTrue("median error ${errors[25]}", errors[25] <= 10f)
    }

    @Test fun flatSignalGivesNoDirection() {
        val acc = RadarAccumulator()
        for (h in 0 until 360 step 5) acc.add(h.toFloat(), -60)
        assertNull(acc.strongestBearing())
    }

    @Test fun noAnswerUntilEnoughOfCircleCovered() {
        val acc = RadarAccumulator()
        for (h in 0 until 90 step 5) acc.add(h.toFloat(), -60)   // a quarter turn only
        assertNull(acc.strongestBearing())
        assertEquals(0.25f, acc.coverage(), 0.03f)
    }

    @Test fun headingWrapsIntoSectors() {
        val acc = RadarAccumulator(36)
        assertEquals(0, acc.sectorOf(0.5f))
        assertEquals(35, acc.sectorOf(359.5f))
        assertEquals(35, acc.sectorOf(-1f))      // negative headings wrap
        assertEquals(0, acc.sectorOf(360f))
        assertEquals(1, acc.sectorOf(370f))
    }

    @Test fun compassPoints() {
        assertEquals("N", RadarAccumulator.compassPoint(359f))
        assertEquals("NE", RadarAccumulator.compassPoint(40f))
        assertEquals("SW", RadarAccumulator.compassPoint(225f))
    }

    @Test fun trendDetectsWarmerAndColder() {
        val t = TrendDetector(windowMs = 2000, thresholdDb = 3f)
        for (ms in 0L..4000L step 250) t.add(ms, if (ms < 2000) -70 else -60)
        assertEquals(TrendDetector.Trend.WARMER, t.trend(4000))
        val c = TrendDetector()
        for (ms in 0L..4000L step 250) c.add(ms, if (ms < 2000) -55 else -65)
        assertEquals(TrendDetector.Trend.COLDER, c.trend(4000))
        val s = TrendDetector()
        for (ms in 0L..4000L step 250) s.add(ms, -60)
        assertEquals(TrendDetector.Trend.STEADY, s.trend(4000))
    }
}
