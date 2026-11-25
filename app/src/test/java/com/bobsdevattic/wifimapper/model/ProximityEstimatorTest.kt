package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityEstimatorTest {

    @Test fun singleSpikeIsRejected() {
        val p = ProximityEstimator()
        repeat(6) { p.add(-80, it * 200L) }
        val before = p.band()
        p.add(-45, 1_300)                         // one freak packet
        assertTrue("smoothed=${p.smoothed}", p.smoothed!! < -75f)
        assertEquals("a single spike must not change the band", before, p.band())
    }

    @Test fun approachingRaisesLevelAndSpeedsUpBeeps() {
        val p = ProximityEstimator()
        repeat(10) { p.add(-90, it * 200L) }
        val farLevel = p.level(); val farBeep = p.beepIntervalMs()
        repeat(15) { p.add(-45, 2_000 + it * 200L) }
        assertTrue(p.level() > farLevel)
        assertTrue(p.beepIntervalMs() < farBeep)
        assertTrue(p.band() >= ProximityEstimator.Band.VERY_CLOSE)
    }

    @Test fun bandsAreOrderedAndBeepIntervalBounded() {
        val p = ProximityEstimator()
        assertEquals(0f, p.level())               // nothing heard yet
        assertEquals(1600L, p.beepIntervalMs())
        repeat(20) { p.add(-30, it * 100L) }      // stronger than "near" saturates
        assertEquals(1f, p.level())
        assertEquals(120L, p.beepIntervalMs())
        assertEquals(ProximityEstimator.Band.HERE, p.band())
    }

    @Test fun detectsWalkingPastTheDevice() {
        val p = ProximityEstimator()
        repeat(10) { p.add(-50, it * 200L) }      // close
        assertFalse(p.belowPeak())
        repeat(15) { p.add(-70, 2_000 + it * 200L) }  // walked away
        assertTrue(p.belowPeak())
    }

    @Test fun lastHeardAndReset() {
        val p = ProximityEstimator()
        assertNull(p.secondsSinceHeard(10_000))
        p.add(-60, 1_000)
        assertEquals(9L, p.secondsSinceHeard(10_000))
        p.reset()
        assertNull(p.smoothed)
        assertEquals(0, p.samples)
    }
}
