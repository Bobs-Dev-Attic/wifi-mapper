package com.bobsdevattic.wifimapper.capture

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.bobsdevattic.wifimapper.model.SignalDetails
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Bluetooth Low Energy scanning. Unlike Wi-Fi, Android doesn't throttle BLE RSSI
 * updates to a few per minute: advertisers are typically heard several times a
 * second, which makes BLE well suited to the radar.
 *
 * Note: Android also rate-limits *starting* scans (~5 starts per 30 s per app),
 * so callers should keep one long-running scan rather than restarting often.
 */
class BleScanner(context: Context) {

    private val adapter = (context.applicationContext
        .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = try { adapter?.isEnabled == true } catch (e: SecurityException) { false }

    sealed interface Status {
        data object Ok : Status
        data object Off : Status
        data object Unsupported : Status
        data class Failed(val reason: String) : Status
    }

    /**
     * Emits the current set of nearby devices (strongest first) as advertisements
     * arrive. Devices not heard for [staleAfterMs] drop out. [onStatus] reports
     * if scanning couldn't start.
     */
    @SuppressLint("MissingPermission")
    fun devices(
        onlyAddress: String? = null,
        staleAfterMs: Long = 30_000,
        onStatus: (Status) -> Unit = {},
    ): Flow<List<BleDeviceInfo>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        when {
            adapter == null -> { onStatus(Status.Unsupported); close(); return@callbackFlow }
            !isEnabled || scanner == null -> { onStatus(Status.Off); close(); return@callbackFlow }
        }
        val seen = LinkedHashMap<String, BleDeviceInfo>()

        fun publish() {
            val now = System.currentTimeMillis()
            seen.values.removeAll { now - it.lastSeenMs > staleAfterMs }
            trySend(seen.values.sortedByDescending { it.rssi })
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                seen[result.device.address] = result.toInfo(); publish()
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { seen[it.device.address] = it.toInfo() }; publish()
            }
            override fun onScanFailed(errorCode: Int) {
                onStatus(Status.Failed(scanErrorText(errorCode)))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        val filters = onlyAddress?.let { listOf(ScanFilter.Builder().setDeviceAddress(it).build()) }

        try {
            scanner!!.startScan(filters, settings, callback)
            onStatus(Status.Ok)
        } catch (e: SecurityException) {
            onStatus(Status.Failed("Bluetooth permission not granted")); close(); return@callbackFlow
        }
        awaitClose {
            try { scanner.stopScan(callback) } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun ScanResult.toInfo(): BleDeviceInfo {
        val record = scanRecord
        val mfgData = record?.manufacturerSpecificData
        val companyId = if (mfgData != null && mfgData.size() > 0) mfgData.keyAt(0) else null
        val tx = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && txPower != ScanResult.TX_POWER_NOT_PRESENT -> txPower
            record != null && record.txPowerLevel != Int.MIN_VALUE -> record.txPowerLevel
            else -> null
        }
        return BleDeviceInfo(
            address = device.address,
            // The advertised name doesn't need BLUETOOTH_CONNECT, unlike device.name.
            name = record?.deviceName,
            rssi = rssi,
            txPower = tx,
            manufacturer = companyId?.let(SignalDetails::manufacturerName),
            connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) isConnectable else true,
            lastSeenMs = System.currentTimeMillis(),
        )
    }

    private fun scanErrorText(code: Int) = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already running"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Too many scans started recently — wait 30 s"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning not supported"
        else -> "Scan failed (code $code)"
    }
}
