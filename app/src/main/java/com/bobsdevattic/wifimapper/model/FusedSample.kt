package com.bobsdevattic.wifimapper.model

/**
 * The output of the [com.bobsdevattic.wifimapper.capture.FusionEngine]: a single
 * WiFi observation placed in 3D space by combining ARCore pose, GPS and barometer.
 */
data class FusedSample(
    val timestamp: Long,
    /** AR-local position (metres). */
    val position: Vector3,
    /** Whether ARCore was in TRACKING state when this was captured. */
    val arTracked: Boolean,
    /** Barometric altitude relative to the session baseline, metres (null if no sensor). */
    val baroAltitudeMeters: Float?,
    /** Discrete floor: 0 = baseline storey, +1 one up, -1 one down. */
    val floorIndex: Int,
    val readings: List<ApReading>,
) {
    /** A single access point's signal at this point. */
    data class ApReading(
        val bssid: String,
        val ssid: String,
        val rssi: Int,
        val frequencyMhz: Int,
    )

    /** A GPS fix, used to set a session's geo-anchor. */
    data class GeoFix(
        val lat: Double,
        val lon: Double,
        val altitude: Double,
        val accuracy: Float,
        val heading: Float?,
    )
}
