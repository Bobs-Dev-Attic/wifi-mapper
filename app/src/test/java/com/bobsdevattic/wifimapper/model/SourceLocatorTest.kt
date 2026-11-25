package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.random.Random

class SourceLocatorTest {

    /** Walk a 10 × 8 m grid at 0.6 m spacing; indoor path loss + noise. */
    private fun survey(source: Vector3, seed: Int, noiseDb: Double = 3.0): List<Pair<Vector3, Int>> {
        val r = Random(seed)
        val out = ArrayList<Pair<Vector3, Int>>()
        var x = 0f
        while (x <= 10f) {
            var z = 0f
            while (z <= 8f) {
                val p = Vector3(x, 0f, z)
                val d = maxOf(p.distanceTo(source), 0.3f)
                val rssi = -33 - 27 * log10(d) + r.nextDouble(-noiseDb, noiseDb)
                out += p to rssi.toInt()
                z += 0.6f
            }
            x += 0.6f
        }
        return out
    }

    @Test fun locatesSourceInsideSurveyedArea() {
        for ((src, seed) in listOf(Vector3(3f, 0.5f, 4f) to 1, Vector3(7.5f, 1f, 2f) to 2, Vector3(5f, 0f, 6.5f) to 3)) {
            val est = SourceLocator.estimate(survey(src, seed))!!
            val horizErr = Vector3(est.position.x - src.x, 0f, est.position.z - src.z).length()
            assertTrue("src=$src est=${est.position} err=$horizErr", horizErr <= 1.5f)
        }
    }

    @Test fun needsEnoughPoints() {
        assertNull(SourceLocator.estimate(listOf(Vector3.ZERO to -50, Vector3(1f, 0f, 0f) to -55)))
    }

    @Test fun singleDominantPointWins() {
        val est = SourceLocator.estimate(listOf(
            Vector3(0f, 0f, 0f) to -40, Vector3(5f, 0f, 0f) to -70,
            Vector3(0f, 0f, 5f) to -70, Vector3(5f, 0f, 5f) to -70,
        ))!!
        assertTrue(est.position.length() < 0.1f)
        assertEquals(-40, est.peakRssi)
    }
}
