package com.bobsdevattic.wifimapper.capture

import android.annotation.SuppressLint
import android.content.Context
import com.bobsdevattic.wifimapper.model.FusedSample
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult

/**
 * GPS / fused-location source. Used only to set a session's geo-anchor (a single
 * good-enough fix near the AR origin) and to tag the map with real-world coords.
 *
 * Do NOT rely on this for indoor x/y: horizontal error indoors is often tens of
 * metres and vertical error is worse than a storey height. ARCore does the fine
 * positioning; GPS just georeferences the whole cloud.
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2000L): Flow<FusedSample.GeoFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    FusedSample.GeoFix(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                        accuracy = if (loc.hasAccuracy()) loc.accuracy else Float.NaN,
                        heading = if (loc.hasBearing()) loc.bearing else null,
                    )
                )
            }
        }
        client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
