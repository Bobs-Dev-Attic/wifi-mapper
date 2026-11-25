package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.roundToInt

class WallLossAnalyzerTest {

    // AP at origin, 1 m up. Wall at x = 5 facing +x, 10 m wide, 3 m tall.
    private val ap = Vector3(0f, 1f, 0f)
    private val wall = WallObservation.of(Vector3(5f, 1f, 0f), 1f, 0f, width = 10f, height = 3f)!!

    /** Synthetic survey: log-distance path loss, plus [wallLossDb] beyond x = 5. */
    private fun survey(wallLossDb: Int, bssid: String = "aa:bb"): List<MeasuredPoint> {
        val pts = ArrayList<MeasuredPoint>()
        var x = 1f
        while (x <= 9f) {
            var z = -3f
            while (z <= 3f) {
                val p = Vector3(x, 1f, z)
                val d = maxOf(p.distanceTo(ap), 0.5f)
                val rssi = (-30 - 20 * log10(d) - if (x > 5f) wallLossDb else 0).roundToInt()
                pts += MeasuredPoint(p, mapOf(bssid to rssi))
                z += 0.5f
            }
            x += 0.5f
        }
        return pts
    }

    @Test fun recoversKnownWallLoss() {
        val result = WallLossAnalyzer().analyze(listOf(wall), survey(wallLossDb = 10)).single()
        assertNotNull(result.lossDb)
        assertEquals(10f, result.lossDb!!, 3f)
        assertEquals(LossConfidence.HIGH, result.confidence)
    }

    @Test fun reportsNearZeroWhenWallIsTransparent() {
        val result = WallLossAnalyzer().analyze(listOf(wall), survey(wallLossDb = 0)).single()
        assertNotNull(result.lossDb)
        assertTrue("expected ~0 dB, got ${result.lossDb}", result.lossDb!! <= 3f)
    }

    @Test fun noDataWhenOnlyOneSideWasSurveyed() {
        val oneSide = survey(wallLossDb = 10).filter { it.position.x < 4.5f }
        val result = WallLossAnalyzer().analyze(listOf(wall), oneSide).single()
        assertNull(result.lossDb)
        assertEquals(LossConfidence.NONE, result.confidence)
    }

    @Test fun ignoresPairsThatPassBesideTheWall() {
        // A narrow 1 m wall far off to the side: pairs at z≈0 never pass through it.
        val narrow = WallObservation.of(Vector3(5f, 1f, 20f), 1f, 0f, width = 1f, height = 3f)!!
        val result = WallLossAnalyzer().analyze(listOf(narrow), survey(10)).single()
        assertEquals(0, result.pairCount)
    }

    @Test fun segmentIntersectionRespectsHeight() {
        // Same x crossing but 5 m above a 3 m-tall wall centred at y = 1.
        assertFalse(wall.intersects(Vector3(4f, 6f, 0f), Vector3(6f, 6f, 0f)))
        assertTrue(wall.intersects(Vector3(4f, 1f, 0f), Vector3(6f, 1f, 0f)))
    }

    @Test fun medianOfEvenAndOdd() {
        assertEquals(2f, WallLossAnalyzer.median(listOf(3f, 1f, 2f)))
        assertEquals(2.5f, WallLossAnalyzer.median(listOf(4f, 1f, 2f, 3f)))
        assertNull(WallLossAnalyzer.median(emptyList()))
    }
}
