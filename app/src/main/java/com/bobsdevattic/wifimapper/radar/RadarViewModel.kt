package com.bobsdevattic.wifimapper.radar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.capture.BleScanner
import com.bobsdevattic.wifimapper.capture.HeadingProvider
import com.bobsdevattic.wifimapper.capture.WifiScanner
import com.bobsdevattic.wifimapper.model.RadarAccumulator
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.model.TrendDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/** What the radar is tracking. */
sealed interface RadarTarget {
    val title: String

    /** The network the phone is connected to: RSSI updates continuously. */
    data object ConnectedWifi : RadarTarget { override val title = "Connected Wi-Fi" }

    /** Any Wi-Fi access point: RSSI only updates when Android allows a scan. */
    data class Wifi(val bssid: String, val ssid: String, val freqMhz: Int) : RadarTarget {
        override val title get() = ssid.ifBlank { bssid }
    }

    /** A Bluetooth LE device: RSSI updates several times a second. */
    data class Ble(val address: String, val name: String, val txPower: Int?) : RadarTarget {
        override val title get() = name
    }
}

class RadarViewModel(app: Application) : AndroidViewModel(app) {

    private val wifi = WifiScanner(app)
    private val ble = BleScanner(app)
    private val heading = HeadingProvider(app)
    val settings = (app as WifiMapperApp).settings

    data class UiState(
        val target: RadarTarget = RadarTarget.ConnectedWifi,
        val rssi: Int? = null,
        val headingDeg: Float = 0f,
        val hasCompass: Boolean = true,
        val sweeping: Boolean = false,
        val sectorMeans: List<Float?> = List(36) { null },
        val coverage: Float = 0f,
        val bearingDeg: Float? = null,
        val contrastDb: Float = 0f,
        val trend: TrendDetector.Trend = TrendDetector.Trend.UNKNOWN,
        val distanceM: Double? = null,
        val samples: Int = 0,
        /** True when the source can't update live (throttled Wi-Fi scans). */
        val slowUpdates: Boolean = false,
        val lastUpdateMs: Long = 0,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState(hasCompass = heading.isAvailable))
    val ui: StateFlow<UiState> = _ui

    private val radar = RadarAccumulator(36)
    private val trend = TrendDetector()
    private var jobs = mutableListOf<Job>()
    @Volatile private var currentHeading = 0f

    fun start(target: RadarTarget) {
        stop()
        radar.reset()
        _ui.value = UiState(
            target = target,
            hasCompass = heading.isAvailable,
            slowUpdates = target is RadarTarget.Wifi && !isConnected(target),
        )
        jobs += viewModelScope.launch {
            heading.headings().collect { h ->
                currentHeading = h
                _ui.value = _ui.value.copy(headingDeg = h)
            }
        }
        jobs += viewModelScope.launch {
            rssiSource(target).collect(::onSample)
        }
    }

    private fun isConnected(t: RadarTarget.Wifi) = wifi.connectedBssid()?.equals(t.bssid, ignoreCase = true) == true

    private fun rssiSource(target: RadarTarget): Flow<Int> = when (target) {
        RadarTarget.ConnectedWifi -> pollConnected()
        is RadarTarget.Wifi ->
            // If it's the network we're on, use the fast connected-RSSI path.
            if (isConnected(target)) pollConnected()
            else flow {
                // Nudge scans; Android may throttle to ~4 per 2 minutes.
                viewModelScope.launch { while (true) { wifi.requestScan(); delay(5_000) } }.also { jobs += it }
                wifi.detailedScanResults().collect { list ->
                    list.firstOrNull { it.bssid.equals(target.bssid, ignoreCase = true) }?.let { emit(it.rssi) }
                }
            }.distinctUntilChanged()
        is RadarTarget.Ble -> ble.devices(onlyAddress = target.address, staleAfterMs = 10_000) { status ->
            val msg = when (status) {
                BleScanner.Status.Ok -> null
                BleScanner.Status.Off -> "Bluetooth is off. Turn it on to track this device."
                BleScanner.Status.Unsupported -> "This phone doesn't support Bluetooth LE."
                is BleScanner.Status.Failed -> status.reason
            }
            _ui.value = _ui.value.copy(message = msg)
        }.mapNotNull { list -> list.firstOrNull()?.takeIf { it.address == target.address }?.rssi }
    }

    private fun pollConnected(): Flow<Int> = flow {
        while (true) {
            wifi.connectedRssi()?.let { emit(it) }
                ?: _ui.value.let { if (it.message == null) _ui.value = it.copy(message = "Not connected to Wi-Fi.") }
            delay(300)
        }
    }

    private fun onSample(rssi: Int) {
        val now = System.currentTimeMillis()
        trend.add(now, rssi)
        val s = _ui.value
        if (s.sweeping) radar.add(currentHeading, rssi)
        _ui.value = s.copy(
            rssi = rssi,
            lastUpdateMs = now,
            trend = trend.trend(now),
            samples = s.samples + 1,
            sectorMeans = radar.sectorMeans(),
            coverage = radar.coverage(),
            bearingDeg = radar.strongestBearing(),
            contrastDb = radar.contrastDb(),
            distanceM = when (val t = s.target) {
                is RadarTarget.Ble -> SignalDetails.estimateBleDistanceM(rssi, t.txPower?.let { it - 41 } ?: -59)
                is RadarTarget.Wifi -> SignalDetails.estimateWifiDistanceM(rssi, t.freqMhz)
                RadarTarget.ConnectedWifi -> SignalDetails.estimateWifiDistanceM(rssi, 5000)
            },
            message = if (s.message == "Not connected to Wi-Fi.") null else s.message,
        )
    }

    /** Start a fresh sweep (clears the previous one). */
    fun startSweep() {
        radar.reset()
        _ui.value = _ui.value.copy(
            sweeping = true, sectorMeans = radar.sectorMeans(), coverage = 0f, bearingDeg = null, contrastDb = 0f,
        )
    }

    fun stopSweep() { _ui.value = _ui.value.copy(sweeping = false) }

    fun stop() { jobs.forEach { it.cancel() }; jobs.clear() }

    override fun onCleared() { stop() }
}
