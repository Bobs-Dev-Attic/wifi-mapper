package com.bobsdevattic.wifimapper.capture

import com.bobsdevattic.wifimapper.model.SignalDetails

/** One Wi-Fi access point from a scan, with everything the networks page shows. */
data class WifiNetworkInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channelWidthCode: Int,
    val wifiStandardCode: Int?,
    val capabilities: String,
    val connected: Boolean,
    val ageSeconds: Long,
) {
    val displayName: String get() = ssid.ifBlank { "(hidden network)" }
    val band: String get() = SignalDetails.bandLabel(frequencyMhz)
    val channel: Int? get() = SignalDetails.channelFor(frequencyMhz)
    val width: String get() = SignalDetails.widthLabel(channelWidthCode)
    val standard: String? get() = wifiStandardCode?.let(SignalDetails::standardLabel)
    val security: String get() = SignalDetails.securityOf(capabilities)
    val approxDistanceM: Double get() = SignalDetails.estimateWifiDistanceM(rssi, frequencyMhz)
    val distanceRangeText: String get() = SignalDetails.formatRange(SignalDetails.wifiDistanceRangeM(rssi, frequencyMhz))
}

/** One Bluetooth LE device seen advertising. */
data class BleDeviceInfo(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** Advertised TX power at 1 m (dBm), if the device includes it. */
    val txPower: Int?,
    val manufacturer: String?,
    val connectable: Boolean,
    val lastSeenMs: Long,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: manufacturer?.let { "$it device" } ?: "Unknown device"
    /** Uses the advertised TX power when present (BLE TX power is usually given at 0 m; −41 dB ≈ 1 m). */
    val approxDistanceM: Double get() =
        SignalDetails.estimateBleDistanceM(rssi, txPower?.let { it - 41 } ?: -59)
}

/** A device paired (bonded) with this phone. */
data class PairedDevice(
    val address: String,
    val name: String,
    /** True if it supports Bluetooth LE (LE or dual-mode), so it may advertise. */
    val supportsLe: Boolean,
)
