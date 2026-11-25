package com.bobsdevattic.wifimapper.model

/** A runtime permission the app uses, and why. Pure Kotlin (permission names as strings). */
data class AppPermission(
    val name: String,
    val title: String,
    val purpose: String,
    /** Required for core mapping; others only power optional features. */
    val essential: Boolean,
)

object PermissionCatalog {
    private const val P = "android.permission."

    /** Runtime permissions that apply on a device running [sdkInt]. */
    fun forSdk(sdkInt: Int): List<AppPermission> = buildList {
        add(AppPermission(P + "ACCESS_FINE_LOCATION", "Precise location",
            "Required by Android to read Wi-Fi scans; also sets the map's GPS anchor.", essential = true))
        if (sdkInt >= 33) add(AppPermission(P + "NEARBY_WIFI_DEVICES", "Nearby Wi-Fi devices",
            "Wi-Fi scanning on Android 13 and later.", essential = true))
        add(AppPermission(P + "CAMERA", "Camera",
            "AR tracking while mapping, wall detection and the AR signal view.", essential = true))
        if (sdkInt >= 31) {
            add(AppPermission(P + "BLUETOOTH_SCAN", "Bluetooth scan",
                "Bluetooth list, Find my device, and recording Bluetooth on maps.", essential = false))
            add(AppPermission(P + "BLUETOOTH_CONNECT", "Bluetooth connect",
                "Names of your paired devices in Find my device.", essential = false))
        }
    }
}
