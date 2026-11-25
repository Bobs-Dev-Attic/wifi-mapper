package com.bobsdevattic.wifimapper.model

import kotlin.math.log10
import kotlin.math.pow

/** Human-readable helpers for Wi-Fi / Bluetooth signal details. Pure Kotlin. */
object SignalDetails {

    /** Channel number from centre frequency (MHz), or null if unknown. */
    fun channelFor(freqMhz: Int): Int? = when (freqMhz) {
        2484 -> 14
        in 2412..2472 -> (freqMhz - 2407) / 5
        in 5150..5895 -> (freqMhz - 5000) / 5
        in 5925..7125 -> (freqMhz - 5950) / 5
        else -> null
    }

    fun bandLabel(freqMhz: Int): String = when (freqMhz) {
        in 2401..2495 -> "2.4 GHz"
        in 5150..5895 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "?"
    }

    /** Strongest security mode advertised in a ScanResult.capabilities string. */
    fun securityOf(capabilities: String): String {
        val c = capabilities.uppercase()
        return when {
            "SAE" in c && ("PSK" in c) -> "WPA2/WPA3"
            "SAE" in c -> "WPA3"
            "OWE" in c -> "Enhanced Open"
            "EAP" in c && "WPA3" in c -> "WPA3-Enterprise"
            "EAP" in c -> "WPA2-Enterprise"
            "WPA2" in c || "RSN" in c -> "WPA2"
            "WPA" in c -> "WPA"
            "WEP" in c -> "WEP (insecure)"
            else -> "Open"
        }
    }

    /** ScanResult.channelWidth code → label. */
    fun widthLabel(code: Int): String = when (code) {
        0 -> "20 MHz"; 1 -> "40 MHz"; 2 -> "80 MHz"; 3 -> "160 MHz"; 4 -> "80+80 MHz"; 5 -> "320 MHz"
        else -> "?"
    }

    /** ScanResult.getWifiStandard() code → label (null when unknown). */
    fun standardLabel(code: Int): String? = when (code) {
        1 -> "802.11a/b/g"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6/6E (802.11ax)"
        7 -> "802.11ad"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> null
    }

    /** 0..4 signal bars, like the status bar. */
    fun bars(rssi: Int): Int = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -75 -> 2
        rssi >= -85 -> 1
        else -> 0
    }

    fun qualityLabel(rssi: Int): String = when (bars(rssi)) {
        4 -> "Excellent"; 3 -> "Good"; 2 -> "Fair"; 1 -> "Weak"; else -> "Very weak"
    }

    /**
     * Very rough Wi-Fi distance (m) from RSSI using free-space path loss plus a
     * typical router transmit power. Walls and antennas make this ±2–3×.
     */
    fun estimateWifiDistanceM(rssi: Int, freqMhz: Int, txPowerDbm: Int = 20): Double {
        // FSPL(dB) = 20log10(d_m) + 20log10(f_MHz) − 27.55  →  solve for d
        val pathLoss = txPowerDbm - rssi
        return 10.0.pow((pathLoss - 20 * log10(freqMhz.toDouble()) + 27.55) / 20.0)
    }

    /**
     * Rough BLE distance (m) from RSSI and the advertised (or assumed) RSSI at
     * 1 m. Indoor path-loss exponent ~2.5.
     */
    fun estimateBleDistanceM(rssi: Int, rssiAt1m: Int = -59, exponent: Double = 2.5): Double =
        10.0.pow((rssiAt1m - rssi) / (10 * exponent))

    /** Bluetooth SIG company IDs for a few common manufacturers. */
    fun manufacturerName(companyId: Int): String? = when (companyId) {
        0x004C -> "Apple"
        0x0006 -> "Microsoft"
        0x0075 -> "Samsung"
        0x00E0 -> "Google"
        0x0059 -> "Nordic Semiconductor"
        0x0157 -> "Huami / Amazfit"
        0x038F -> "Xiaomi"
        0x0171 -> "Amazon"
        0x02E5 -> "Espressif"
        0x0087 -> "Garmin"
        else -> null
    }
}
