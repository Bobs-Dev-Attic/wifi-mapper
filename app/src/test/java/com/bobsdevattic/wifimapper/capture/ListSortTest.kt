package com.bobsdevattic.wifimapper.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class ListSortTest {
    private fun wifi(ssid: String, rssi: Int, mhz: Int = 2437, caps: String = "[WPA2-PSK-CCMP]",
                     connected: Boolean = false, age: Long = 0) =
        WifiNetworkInfo(ssid, "bssid-$ssid-$rssi", rssi, mhz, 0, null, caps, connected, age)

    private fun ble(name: String?, rssi: Int, maker: String? = null, connectable: Boolean = true, seen: Long = 0) =
        BleDeviceInfo("addr-$name-$rssi", name, rssi, null, maker, connectable, seen)

    private val nets = listOf(
        wifi("bravo", -60, mhz = 5180, age = 10),
        wifi("", -40, age = 2),                                      // hidden
        wifi("Alpha", -70, mhz = 2412, caps = "[ESS]", age = 30),
        wifi("home", -75, mhz = 5955, connected = true, age = 1),
    )

    @Test fun wifiDefaultPutsConnectedFirstThenStrongest() {
        assertEquals(listOf("home", "", "bravo", "Alpha"), ListSort.wifi(nets, WifiSort.SIGNAL).map { it.ssid })
    }

    @Test fun wifiNameIsCaseInsensitiveWithHiddenLastBothWays() {
        assertEquals(listOf("Alpha", "bravo", "home", ""), ListSort.wifi(nets, WifiSort.NAME).map { it.ssid })
        assertEquals(listOf("home", "bravo", "Alpha", ""), ListSort.wifi(nets, WifiSort.NAME, reversed = true).map { it.ssid })
    }

    @Test fun wifiChannelRecentAndReverse() {
        assertEquals(listOf("Alpha", "", "bravo", "home"), ListSort.wifi(nets, WifiSort.CHANNEL).map { it.ssid })
        assertEquals(listOf("home", "", "bravo", "Alpha"), ListSort.wifi(nets, WifiSort.RECENT).map { it.ssid })
        assertEquals(listOf("Alpha", "bravo", "", "home"), ListSort.wifi(nets, WifiSort.RECENT, reversed = true).map { it.ssid })
    }

    @Test fun wifiDistanceNearestFirstAndSecurityGroupsWithSignalTieBreak() {
        val d = ListSort.wifi(nets, WifiSort.DISTANCE).map { it.approxDistanceM }
        assertEquals(d.sorted(), d)
        val sec = ListSort.wifi(nets, WifiSort.SECURITY)
        assertEquals("Alpha", sec.first().ssid)                       // open network groups apart
        // Same security: strongest first.
        assertEquals(listOf("", "bravo", "home"), sec.drop(1).map { it.ssid })
    }

    private val devs = listOf(
        ble("Buds", -70, maker = "Samsung", seen = 100),
        ble(null, -50, maker = "Apple", connectable = false, seen = 300),
        ble("watch", -60, seen = 200),
        ble("Alpha tag", -80, maker = "apple", connectable = false, seen = 50),
    )

    @Test fun bleSorts() {
        assertEquals(listOf(-50, -60, -70, -80), ListSort.ble(devs, BleSort.SIGNAL).map { it.rssi })
        assertEquals(listOf(-80, -70, -60, -50), ListSort.ble(devs, BleSort.SIGNAL, reversed = true).map { it.rssi })
        assertEquals(listOf("Alpha tag", "Buds", "watch", null), ListSort.ble(devs, BleSort.NAME).map { it.name })
        assertEquals(listOf("watch", "Buds", "Alpha tag", null), ListSort.ble(devs, BleSort.NAME, reversed = true).map { it.name })
        // Maker A→Z (case-insensitive), then name; unknown maker last.
        assertEquals(listOf(-80, -50, -70, -60), ListSort.ble(devs, BleSort.MAKER).map { it.rssi })
        assertEquals(listOf(-60, -70, -50, -80), ListSort.ble(devs, BleSort.TYPE).map { it.rssi })
        assertEquals(listOf(300L, 200L, 100L, 50L), ListSort.ble(devs, BleSort.RECENT).map { it.lastSeenMs })
        val d = ListSort.ble(devs, BleSort.DISTANCE).map { it.approxDistanceM }
        assertEquals(d.sorted(), d)
    }

    @Test fun pairedSortsUseLiveSignalWithUnheardLast() {
        val paired = listOf(
            PairedDevice("a1", "Zed buds", true),
            PairedDevice("a2", "car", false),          // classic-only: never heard
            PairedDevice("a3", "Alpha watch", true),
            PairedDevice("a4", "Desk", true),          // not heard right now
        )
        val heard = mapOf(
            "a1" to ble("Zed buds", -55, maker = "Samsung", seen = 10),
            "a3" to ble("Alpha watch", -75, maker = "Google", seen = 20),
        )
        val of: (PairedDevice) -> BleDeviceInfo? = { heard[it.address] }
        fun names(sort: BleSort, rev: Boolean = false) = ListSort.paired(paired, of, sort, rev).map { it.name }

        assertEquals(listOf("Zed buds", "Alpha watch", "car", "Desk"), names(BleSort.SIGNAL))
        assertEquals(listOf("Alpha watch", "Zed buds", "car", "Desk"), names(BleSort.SIGNAL, rev = true))
        assertEquals(listOf("Alpha watch", "car", "Desk", "Zed buds"), names(BleSort.NAME))
        assertEquals(listOf("Zed buds", "Desk", "car", "Alpha watch"), names(BleSort.NAME, rev = true))
        assertEquals(listOf("Alpha watch", "Zed buds", "car", "Desk"), names(BleSort.MAKER))
        assertEquals(listOf("Alpha watch", "Zed buds", "car", "Desk"), names(BleSort.RECENT))
    }
}
