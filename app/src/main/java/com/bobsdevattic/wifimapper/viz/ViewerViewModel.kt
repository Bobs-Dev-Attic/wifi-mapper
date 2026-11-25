package com.bobsdevattic.wifimapper.viz

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.model.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ViewerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as WifiMapperApp).repository
    private val prefs = app.getSharedPreferences("viewer_settings", Context.MODE_PRIVATE)

    data class UiState(
        val sessionName: String = "",
        val points: List<CloudPoint> = emptyList(),
        val floorFilter: Int? = null,     // null = all floors
        val availableFloors: List<Int> = emptyList(),
        val stats: CoverageStats = CoverageStats.from(emptyList()),
        val palette: SignalPalette = SignalPalette.DEFAULT,
        val loading: Boolean = true,
    )

    private val _ui = MutableStateFlow(
        UiState(palette = SignalPalette.fromName(prefs.getString(KEY_PALETTE, null)))
    )
    val ui: StateFlow<UiState> = _ui

    private var allPoints: List<CloudPoint> = emptyList()

    fun load(sessionId: Long) {
        viewModelScope.launch {
            val session = repo.getSession(sessionId)
            val palette = _ui.value.palette
            allPoints = repo.getPoints(sessionId).map { pwr ->
                CloudPoint(
                    position = Vector3(pwr.point.x, pwr.point.y, pwr.point.z),
                    color = SignalColor.forRssi(pwr.point.bestRssi, palette),
                    floor = pwr.point.floorIndex,
                    rssi = pwr.point.bestRssi,
                )
            }
            _ui.value = _ui.value.copy(
                sessionName = session?.name ?: "Session $sessionId",
                availableFloors = allPoints.map { it.floor }.distinct().sorted(),
                loading = false,
            )
            applyFilter(_ui.value.floorFilter)
        }
    }

    fun setFloorFilter(floor: Int?) = applyFilter(floor)

    /** Switch colour scale, remember it, and recolour every point. */
    fun setPalette(palette: SignalPalette) {
        prefs.edit().putString(KEY_PALETTE, palette.name).apply()
        allPoints = allPoints.map { it.copy(color = SignalColor.forRssi(it.rssi, palette)) }
        _ui.value = _ui.value.copy(palette = palette)
        applyFilter(_ui.value.floorFilter)
    }

    // Stats are computed from the exact list instances shown, so the MAX/MIN
    // markers (matched by reference in PointCloud3DView) stay in sync.
    private fun applyFilter(floor: Int?) {
        val shown = if (floor == null) allPoints else allPoints.filter { it.floor == floor }
        _ui.value = _ui.value.copy(
            floorFilter = floor,
            points = shown,
            stats = CoverageStats.from(shown),
        )
    }

    private companion object {
        const val KEY_PALETTE = "signal_palette"
    }
}
