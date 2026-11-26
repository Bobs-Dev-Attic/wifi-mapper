package com.bobsdevattic.wifimapper.capture

/** Ways to order the Wi-Fi list. The first is the default. */
enum class WifiSort(val label: String) {
    SIGNAL("Signal strength"),
    NAME("Name"),
    DISTANCE("Distance"),
    CHANNEL("Band & channel"),
    SECURITY("Security"),
    RECENT("Recently seen"),
}

/** Ways to order the Bluetooth list. The first is the default. */
enum class BleSort(val label: String) {
    SIGNAL("Signal strength"),
    NAME("Name"),
    DISTANCE("Distance"),
    MAKER("Maker"),
    TYPE("Type (connectable first)"),
    RECENT("Recently seen"),
}

/**
 * Sorting for the nearby-signal lists. Each sort has a natural direction
 * (strongest, nearest, A→Z, newest first); [reversed] flips only that key.
 * Items missing the key (hidden networks, unnamed devices, unknown maker) stay
 * at the bottom either way, and ties fall back to strongest signal first.
 */
object ListSort {

    fun wifi(list: List<WifiNetworkInfo>, sort: WifiSort, reversed: Boolean = false): List<WifiNetworkInfo> {
        val missing: (WifiNetworkInfo) -> Boolean = { sort == WifiSort.NAME && it.ssid.isBlank() }
        val key: Comparator<WifiNetworkInfo> = when (sort) {
            // The network you're connected to leads the default view.
            WifiSort.SIGNAL -> compareByDescending<WifiNetworkInfo> { it.connected }.thenByDescending { it.rssi }
            WifiSort.NAME -> compareBy { it.ssid.lowercase() }
            WifiSort.DISTANCE -> compareBy { it.approxDistanceM }
            WifiSort.CHANNEL -> compareBy { it.frequencyMhz }
            WifiSort.SECURITY -> compareBy { it.security.lowercase() }
            WifiSort.RECENT -> compareBy { it.ageSeconds }
        }
        return list.sortedWith(build(missing, key, reversed) { it.rssi })
    }

    fun ble(list: List<BleDeviceInfo>, sort: BleSort, reversed: Boolean = false): List<BleDeviceInfo> {
        val missing: (BleDeviceInfo) -> Boolean = when (sort) {
            BleSort.NAME -> { d -> d.name.isNullOrBlank() }
            BleSort.MAKER -> { d -> d.manufacturer.isNullOrBlank() }
            else -> { _ -> false }
        }
        return list.sortedWith(build(missing, bleKey(sort), reversed) { it.rssi })
    }

    /**
     * Your paired devices, ordered by [sort]. Name uses the paired name; every
     * other sort uses the live advertisement from [heardOf], with devices not
     * heard right now kept at the bottom (alphabetically).
     */
    fun paired(
        list: List<PairedDevice>,
        heardOf: (PairedDevice) -> BleDeviceInfo?,
        sort: BleSort,
        reversed: Boolean = false,
    ): List<PairedDevice> {
        val withHeard = list.map { it to heardOf(it) }
        val missing: (Pair<PairedDevice, BleDeviceInfo?>) -> Boolean = { (_, h) -> sort != BleSort.NAME && h == null }
        val key: Comparator<Pair<PairedDevice, BleDeviceInfo?>> =
            if (sort == BleSort.NAME) compareBy { it.first.name.lowercase() }
            else Comparator { a, b ->
                val ha = a.second; val hb = b.second
                if (ha == null || hb == null) 0 else bleKey(sort).compare(ha, hb)
            }
        return withHeard
            .sortedWith(build(missing, key, reversed) { it.second?.rssi ?: Int.MIN_VALUE }
                .thenBy { it.first.name.lowercase() })
            .map { it.first }
    }

    private fun bleKey(sort: BleSort): Comparator<BleDeviceInfo> = when (sort) {
        BleSort.SIGNAL -> compareByDescending { it.rssi }
        BleSort.NAME -> compareBy { it.name.orEmpty().lowercase() }
        BleSort.DISTANCE -> compareBy { it.approxDistanceM }
        BleSort.MAKER -> compareBy<BleDeviceInfo> { it.manufacturer.orEmpty().lowercase() }
            .thenBy { it.displayName.lowercase() }
        BleSort.TYPE -> compareByDescending { it.connectable }
        BleSort.RECENT -> compareByDescending { it.lastSeenMs }
    }

    private fun <T> build(
        missing: (T) -> Boolean,
        key: Comparator<T>,
        reversed: Boolean,
        rssi: (T) -> Int,
    ): Comparator<T> =
        compareBy(missing)                                   // false (has the key) first
            .then(if (reversed) key.reversed() else key)
            .thenByDescending(rssi)
}
