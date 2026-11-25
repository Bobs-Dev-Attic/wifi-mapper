package com.bobsdevattic.wifimapper.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionCatalogTest {
    private fun names(sdk: Int) = PermissionCatalog.forSdk(sdk).map { it.name.substringAfterLast('.') }

    @Test fun android11NeedsOnlyLocationAndCamera() {
        assertEquals(listOf("ACCESS_FINE_LOCATION", "CAMERA"), names(30))
    }

    @Test fun android12AddsBluetoothRuntimePermissions() {
        assertEquals(listOf("ACCESS_FINE_LOCATION", "CAMERA", "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT"), names(31))
    }

    @Test fun android15IncludesNearbyWifi() {
        val n = names(35)
        assertTrue("NEARBY_WIFI_DEVICES" in n)
        assertEquals(5, n.size)
        assertTrue(PermissionCatalog.forSdk(35).filter { it.essential }.all { it.name.endsWith("LOCATION") || it.name.endsWith("WIFI_DEVICES") || it.name.endsWith("CAMERA") })
    }
}
