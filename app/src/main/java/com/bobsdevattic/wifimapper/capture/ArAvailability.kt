package com.bobsdevattic.wifimapper.capture

import android.content.Context
import com.google.ar.core.ArCoreApk

/**
 * Best-effort synchronous check for ARCore support. The first call may return
 * while the Play Services for AR check is still in flight (UNKNOWN_CHECKING);
 * we optimistically treat that as "available" and let SceneView surface a real
 * error if the device truly can't do AR. The Galaxy S25 Edge is on the ARCore
 * supported-device list, so this returns true there.
 */
fun arAvailable(context: Context): Boolean = try {
    val availability = ArCoreApk.getInstance().checkAvailability(context)
    availability.isSupported || availability == ArCoreApk.Availability.UNKNOWN_CHECKING
} catch (t: Throwable) {
    false
}
