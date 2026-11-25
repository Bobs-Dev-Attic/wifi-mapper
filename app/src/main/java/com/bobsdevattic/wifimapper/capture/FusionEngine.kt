package com.bobsdevattic.wifimapper.capture

import com.bobsdevattic.wifimapper.model.FusedSample
import com.bobsdevattic.wifimapper.model.Vector3

/**
 * Combines the three positioning sources into a single [FusedSample]:
 *
 *  - ARCore pose -> fine-grained (x, y, z) in the session-local frame.
 *  - Barometer   -> independent vertical estimate -> floor index (preferred over
 *                   AR's Y, which drifts). Falls back to AR Y when no sensor.
 *  - GPS         -> not used per-sample; it sets the session geo-anchor elsewhere.
 *
 * This class is deliberately free of Android framework types so it can be unit
 * tested on the JVM.
 */
class FusionEngine(
    private val storeyHeightMeters: Float = 3.0f,
) {

    /**
     * @param pose latest AR pose state
     * @param pressureHpa latest barometric pressure, or null if unavailable
     * @param baselineHpa session baseline pressure, or null
     * @param readings WiFi APs observed at this instant
     */
    fun fuse(
        pose: PoseSource.PoseState,
        pressureHpa: Float?,
        baselineHpa: Float?,
        readings: List<FusedSample.ApReading>,
    ): FusedSample {
        val baroAltitude: Float? =
            if (pressureHpa != null && baselineHpa != null)
                BarometerProvider.relativeAltitude(pressureHpa, baselineHpa)
            else null

        val floor: Int = when {
            baroAltitude != null -> BarometerProvider.floorFromAltitude(baroAltitude, storeyHeightMeters)
            else -> Math.round(pose.position.y / storeyHeightMeters)
        }

        // If the barometer gives a trustworthy altitude and AR isn't tracking,
        // substitute baro altitude for Y so the point still lands on the right level.
        val position: Vector3 =
            if (!pose.tracking && baroAltitude != null)
                pose.position.copy(y = baroAltitude)
            else pose.position

        return FusedSample(
            timestamp = System.currentTimeMillis(),
            position = position,
            arTracked = pose.tracking,
            baroAltitudeMeters = baroAltitude,
            floorIndex = floor,
            readings = readings,
        )
    }
}
