package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalDetailsTest {
    @Test fun channels() {
        assertEquals(1, SignalDetails.channelFor(2412))
        assertEquals(6, SignalDetails.channelFor(2437))
        assertEquals(14, SignalDetails.channelFor(2484))
        assertEquals(36, SignalDetails.channelFor(5180))
        assertEquals(149, SignalDetails.channelFor(5745))
        assertEquals(5, SignalDetails.channelFor(5975))   // 6 GHz ch 5
        assertNull(SignalDetails.channelFor(900))
    }

    @Test fun security() {
        assertEquals("WPA3", SignalDetails.securityOf("[RSN-SAE-CCMP][ESS]"))
        assertEquals("WPA2/WPA3", SignalDetails.securityOf("[RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals("WPA2", SignalDetails.securityOf("[WPA2-PSK-CCMP][ESS]"))
        assertEquals("WPA2-Enterprise", SignalDetails.securityOf("[WPA2-EAP-CCMP][ESS]"))
        assertEquals("Open", SignalDetails.securityOf("[ESS]"))
    }

    @Test fun barsAndDistance() {
        assertEquals(4, SignalDetails.bars(-50))
        assertEquals(0, SignalDetails.bars(-90))
        // Stronger signal ⇒ shorter estimated distance.
        assertTrue(SignalDetails.estimateWifiDistanceM(-40, 5180) < SignalDetails.estimateWifiDistanceM(-70, 5180))
        assertEquals(1.0, SignalDetails.estimateBleDistanceM(-59), 0.01)
    }
}
