package com.bobsdevattic.wifimapper.viz

import com.bobsdevattic.wifimapper.data.SamplePointWithReadings

/** What the 3D map is coloured by. */
data class SignalSource(val key: String, val label: String, val kind: Kind, val pointCount: Int) {
    enum class Kind { STRONGEST_WIFI, WIFI_NETWORK, BLUETOOTH }

    companion object {
        val STRONGEST = SignalSource("best", "Strongest Wi-Fi (any network)", Kind.STRONGEST_WIFI, 0)
    }
}

/** An estimated transmitter location to draw on the map. */
data class MapMarker(
    val position: com.bobsdevattic.wifimapper.model.Vector3,
    val label: String,
    val floor: Int,
)

/** Builds the list of colour-by sources present in a map, and reads RSSI per point. */
object SignalSourceCatalog {

    private fun wifiKey(ssid: String, bssid: String) = "wifi:" + ssid.ifBlank { bssid }
    private fun bleKey(address: String) = "ble:" + address.uppercase()

    fun build(points: List<SamplePointWithReadings>, maxPerKind: Int = 20): List<SignalSource> {
        val wifi = HashMap<String, Pair<String, MutableSet<Long>>>()
        val ble = HashMap<String, Pair<String, MutableSet<Long>>>()
        for (p in points) {
            for (r in p.readings) {
                val k = wifiKey(r.ssid, r.bssid)
                wifi.getOrPut(k) { r.ssid.ifBlank { "(hidden) ${r.bssid}" } to HashSet() }.second += p.point.id
            }
            for (b in p.bleReadings) {
                val k = bleKey(b.address)
                val entry = ble.getOrPut(k) { (b.name ?: b.address) to HashSet() }
                if (entry.first == b.address && b.name != null) ble[k] = b.name to entry.second   // prefer a real name
                ble.getValue(k).second += p.point.id
            }
        }
        fun top(m: Map<String, Pair<String, Set<Long>>>, kind: SignalSource.Kind) =
            m.map { (k, v) -> SignalSource(k, v.first, kind, v.second.size) }
                .sortedByDescending { it.pointCount }.take(maxPerKind)
        return listOf(SignalSource.STRONGEST) +
            top(wifi, SignalSource.Kind.WIFI_NETWORK) +
            top(ble, SignalSource.Kind.BLUETOOTH)
    }

    /** RSSI of [source] at a point, or null if it wasn't heard there. */
    fun rssiAt(p: SamplePointWithReadings, source: SignalSource): Int? = when (source.kind) {
        SignalSource.Kind.STRONGEST_WIFI -> p.point.bestRssi
        SignalSource.Kind.WIFI_NETWORK ->
            p.readings.filter { wifiKey(it.ssid, it.bssid) == source.key }.maxOfOrNull { it.rssi }
        SignalSource.Kind.BLUETOOTH ->
            p.bleReadings.filter { bleKey(it.address) == source.key }.maxOfOrNull { it.rssi }
    }

    /**
     * Individual transmitters behind a source, each as (label, per-point RSSI
     * picker). A Wi-Fi network may have several access points (mesh nodes,
     * 2.4/5 GHz radios); each gets its own location estimate.
     */
    fun transmitters(points: List<SamplePointWithReadings>, source: SignalSource):
        List<Pair<String, (SamplePointWithReadings) -> Int?>> = when (source.kind) {
        SignalSource.Kind.STRONGEST_WIFI -> emptyList()
        SignalSource.Kind.BLUETOOTH -> listOf(source.label to { p: SamplePointWithReadings -> rssiAt(p, source) })
        SignalSource.Kind.WIFI_NETWORK -> {
            val bssids = points.flatMap { p -> p.readings.filter { wifiKey(it.ssid, it.bssid) == source.key } }
                .map { it.bssid.lowercase() }.distinct()
            // Radios of one physical AP have near-identical BSSIDs; group by all but the last byte.
            bssids.groupBy { it.substringBeforeLast(':') }.values.mapIndexed { i, group ->
                val label = if (bssids.size == 1) source.label else "${source.label} · AP ${i + 1}"
                label to { p: SamplePointWithReadings ->
                    p.readings.filter { it.bssid.lowercase() in group }.maxOfOrNull { it.rssi }
                }
            }
        }
    }
}
