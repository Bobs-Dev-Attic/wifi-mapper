package com.bobsdevattic.wifimapper.capture

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.model.FusedSample
import com.bobsdevattic.wifimapper.model.Vector3
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CaptureViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as WifiMapperApp
    private val repo = appRef.repository
    val poseSource = appRef.poseSource
    val wallSource = appRef.wallSource

    private val wifi = WifiScanner(app)
    private val location = LocationProvider(app)
    private val barometer = BarometerProvider(app)
    private val fusion = FusionEngine()

    data class UiState(
        val sessionId: Long? = null,
        val sessionName: String = "",
        val pointCount: Int = 0,
        val tracking: Boolean = false,
        val floor: Int = 0,
        val visibleAps: Int = 0,
        val bestRssi: Int? = null,
        val hasBarometer: Boolean = true,
        val anchorSet: Boolean = false,
        val wallsDetected: Int = 0,
        val autoCapture: Boolean = true,
        val status: String = "Idle",
    )

    private val _ui = MutableStateFlow(UiState(hasBarometer = barometer.hasBarometer))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // Latest sensor snapshots.
    @Volatile private var latestReadings: List<FusedSample.ApReading> = emptyList()
    @Volatile private var latestPressure: Float? = null
    @Volatile private var baselinePressure: Float? = null
    @Volatile private var lastSamplePos: Vector3? = null

    private var jobs = mutableListOf<Job>()

    /** Minimum movement (m) before auto-capture drops another sample. */
    private val autoCaptureSpacingM = 0.6f

    fun startSession(name: String) {
        if (_ui.value.sessionId != null) return
        viewModelScope.launch {
            wallSource.clear()   // a new AR session starts with a fresh frame
            val sessionId = repo.startSession(name, baselinePressure)
            _ui.value = _ui.value.copy(
                sessionId = sessionId, sessionName = name, status = "Capturing",
            )
            collectSensors(sessionId)
        }
    }

    private fun collectSensors(sessionId: Long) {
        // WiFi scans.
        jobs += viewModelScope.launch {
            wifi.scanResults().collect { readings ->
                latestReadings = readings
                _ui.value = _ui.value.copy(
                    visibleAps = readings.size,
                    bestRssi = readings.maxByOrNull { it.rssi }?.rssi,
                )
            }
        }
        // Barometer -> baseline + live pressure.
        if (barometer.hasBarometer) {
            jobs += viewModelScope.launch {
                barometer.pressureHpa().collect { p ->
                    latestPressure = p
                    if (baselinePressure == null) {
                        baselinePressure = p
                        repo.setBaseline(sessionId, p)
                    }
                }
            }
        }
        // GPS -> set the geo-anchor from the first sufficiently-accurate fix.
        jobs += viewModelScope.launch {
            location.locationUpdates().collect { fix ->
                if (!_ui.value.anchorSet && (fix.accuracy.isNaN() || fix.accuracy < 30f)) {
                    repo.setAnchor(sessionId, fix)
                    _ui.value = _ui.value.copy(anchorSet = true)
                }
            }
        }
        // Walls seen by ARCore -> live count, plus a periodic save so a crash or
        // app kill doesn't lose them. (Also saved on stop.)
        jobs += viewModelScope.launch {
            wallSource.walls.collect { walls ->
                _ui.value = _ui.value.copy(wallsDetected = walls.size)
            }
        }
        jobs += viewModelScope.launch {
            while (true) {
                delay(WALL_AUTOSAVE_MS)
                repo.replaceWalls(sessionId, wallSource.walls.value)
            }
        }
        // Pose stream -> update tracking state + auto-capture on movement.
        jobs += viewModelScope.launch {
            poseSource.state.collect { pose ->
                val floor = currentFloor(pose)
                _ui.value = _ui.value.copy(tracking = pose.tracking, floor = floor)
                if (_ui.value.autoCapture && pose.tracking) {
                    val last = lastSamplePos
                    if (last == null || pose.position.distanceTo(last) >= autoCaptureSpacingM) {
                        recordSample()
                    }
                }
            }
        }
    }

    private fun currentFloor(pose: PoseSource.PoseState): Int {
        val p = latestPressure; val b = baselinePressure
        return if (p != null && b != null)
            BarometerProvider.floorFromAltitude(BarometerProvider.relativeAltitude(p, b))
        else Math.round(pose.position.y / 3f)
    }

    /** Capture a sample at the current pose/readings (manual button or auto). */
    fun recordSample() {
        val sessionId = _ui.value.sessionId ?: return
        val readings = latestReadings
        if (readings.isEmpty()) {
            _ui.value = _ui.value.copy(status = "No WiFi results yet (scan throttled?)")
            return
        }
        val sample = fusion.fuse(poseSource.current(), latestPressure, baselinePressure, readings)
        lastSamplePos = sample.position
        viewModelScope.launch {
            repo.saveSample(sessionId, sample)
            _ui.value = _ui.value.copy(
                pointCount = _ui.value.pointCount + 1,
                floor = sample.floorIndex,
                status = "Captured point #${_ui.value.pointCount + 1}",
            )
        }
        // Nudge a fresh scan for next time (may be throttled; that's fine).
        wifi.requestScan()
    }

    fun toggleAutoCapture() {
        _ui.value = _ui.value.copy(autoCapture = !_ui.value.autoCapture)
    }

    fun stopSession() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        // Final wall save with the most complete snapshot ARCore produced.
        _ui.value.sessionId?.let { id ->
            val walls = wallSource.walls.value
            viewModelScope.launch { repo.replaceWalls(id, walls) }
        }
        _ui.value = UiState(hasBarometer = barometer.hasBarometer)
        lastSamplePos = null
        baselinePressure = null
    }

    override fun onCleared() {
        super.onCleared()
        jobs.forEach { it.cancel() }
    }

    private companion object {
        const val WALL_AUTOSAVE_MS = 10_000L
    }
}
