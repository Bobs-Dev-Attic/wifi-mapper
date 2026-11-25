package com.bobsdevattic.wifimapper.capture

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import com.bobsdevattic.wifimapper.model.FusedSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads WiFi scan results.
 *
 * IMPORTANT — Android scan throttling: since Android 9 a foreground app may call
 * [WifiManager.startScan] only ~4 times per 2 minutes; beyond that the system
 * returns the last cached results. So we do NOT spin on startScan(). Instead we:
 *   - listen for SCAN_RESULTS_AVAILABLE_ACTION (fires on any scan, including ones
 *     triggered by the system), and
 *   - optionally nudge a scan via [requestScan], rate-limited by the caller.
 * For denser spatial sampling, also poll the *connected* AP's RSSI
 * (WifiManager.connectionInfo.rssi), which is not throttled — see CaptureViewModel.
 */
class WifiScanner(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /** Emits the latest scan results each time the system publishes a scan. */
    fun scanResults(): Flow<List<FusedSample.ApReading>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(readCurrentResults())
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        appContext.registerReceiver(receiver, filter)
        // Emit whatever is already cached immediately.
        trySend(readCurrentResults())
        requestScan()
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    /**
     * Ask the system for a fresh scan. Returns false if throttled/denied; callers
     * should treat the latest cached [readCurrentResults] as still-valid then.
     */
    @Suppress("DEPRECATION")
    fun requestScan(): Boolean = try {
        wifiManager.startScan()
    } catch (e: SecurityException) {
        false
    }

    /** Snapshot of the most recent scan the OS has (requires location permission). */
    @SuppressLint("MissingPermission")
    fun readCurrentResults(): List<FusedSample.ApReading> = try {
        wifiManager.scanResults.map {
            FusedSample.ApReading(
                bssid = it.BSSID ?: "",
                ssid = it.SSID ?: "",
                rssi = it.level,
                frequencyMhz = it.frequency,
            )
        }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** RSSI (dBm) of the currently-connected network, or null. Not throttled. */
    @SuppressLint("MissingPermission")
    fun connectedRssi(): Int? = try {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo?.rssi?.takeIf { it != -127 && it != 0 }
    } catch (e: SecurityException) {
        null
    }

    val isWifiEnabled: Boolean get() = wifiManager.isWifiEnabled

    /** SSID of the currently-connected network (without quotes), or null. */
    @SuppressLint("MissingPermission")
    fun connectedSsid(): String? = try {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (e: SecurityException) {
        null
    }

    /** BSSID of the currently-connected network, or null. */
    @SuppressLint("MissingPermission")
    fun connectedBssid(): String? = try {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
    } catch (e: SecurityException) {
        null
    }

    /** Full details of every network in the latest scan. */
    @SuppressLint("MissingPermission")
    fun detailedResults(): List<WifiNetworkInfo> = try {
        val connected = connectedBssid()
        val nowUs = SystemClock.elapsedRealtime() * 1000
        wifiManager.scanResults.map { it.toInfo(connected, nowUs) }
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Emits [detailedResults] whenever the system publishes a scan. */
    fun detailedScanResults(): Flow<List<WifiNetworkInfo>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { trySend(detailedResults()) }
        }
        appContext.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        trySend(detailedResults())
        requestScan()
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    @Suppress("DEPRECATION")
    private fun ScanResult.toInfo(connectedBssid: String?, nowUs: Long) = WifiNetworkInfo(
        ssid = SSID?.takeIf { it.isNotBlank() } ?: "",
        bssid = BSSID ?: "",
        rssi = level,
        frequencyMhz = frequency,
        channelWidthCode = channelWidth,
        wifiStandardCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wifiStandard else null,
        capabilities = capabilities ?: "",
        connected = BSSID != null && BSSID.equals(connectedBssid, ignoreCase = true),
        ageSeconds = ((nowUs - timestamp) / 1_000_000).coerceAtLeast(0),
    )
}
