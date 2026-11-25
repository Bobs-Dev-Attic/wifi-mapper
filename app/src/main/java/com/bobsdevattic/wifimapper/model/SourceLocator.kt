package com.bobsdevattic.wifimapper.model

import kotlin.math.pow

/**
 * Estimates where a transmitter (router, mesh node, Bluetooth device) is, from
 * the RSSI it produced at surveyed points: a weighted centroid of the strongest
 * readings, weighting by received POWER (linear, not dB) so the very strongest
 * spots dominate.
 *
 * Limits: it can only place the source within/near the area you walked. A router
 * in a closet you never entered lands at the nearest spot you did walk. Pure
 * Kotlin — unit-testable.
 */
object SourceLocator {

    data class Estimate(val position: Vector3, val usedPoints: Int, val peakRssi: Int)

    fun estimate(readings: List<Pair<Vector3, Int>>, topK: Int = 8, minPoints: Int = 3): Estimate? {
        if (readings.size < minPoints) return null
        val top = readings.sortedByDescending { it.second }.take(topK)
        val peak = top.first().second
        var wx = 0.0; var wy = 0.0; var wz = 0.0; var wsum = 0.0
        for ((p, rssi) in top) {
            val w = 10.0.pow((rssi - peak) / 10.0)        // 1.0 at the peak, 0.1 at −10 dB
            wx += w * p.x; wy += w * p.y; wz += w * p.z; wsum += w
        }
        return Estimate(
            Vector3((wx / wsum).toFloat(), (wy / wsum).toFloat(), (wz / wsum).toFloat()),
            usedPoints = top.size,
            peakRssi = peak,
        )
    }
}
