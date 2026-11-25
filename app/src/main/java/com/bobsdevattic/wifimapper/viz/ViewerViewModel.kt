package com.bobsdevattic.wifimapper.viz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.model.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ViewerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as WifiMapperApp).repository

    data class UiState(
        val sessionName: String = "",
        val points: List<CloudPoint> = emptyList(),
        val floorFilter: Int? = null,     // null = all floors
        val availableFloors: List<Int> = emptyList(),
        val loading: Boolean = true,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var allPoints: List<CloudPoint> = emptyList()

    fun load(sessionId: Long) {
        viewModelScope.launch {
            val session = repo.getSession(sessionId)
            val points = repo.getPoints(sessionId).map { pwr ->
                CloudPoint(
                    position = Vector3(pwr.point.x, pwr.point.y, pwr.point.z),
                    color = SignalColor.forRssi(pwr.point.bestRssi),
                    floor = pwr.point.floorIndex,
                )
            }
            allPoints = points
            _ui.value = UiState(
                sessionName = session?.name ?: "Session $sessionId",
                points = points,
                availableFloors = points.map { it.floor }.distinct().sorted(),
                loading = false,
            )
        }
    }

    fun setFloorFilter(floor: Int?) {
        _ui.value = _ui.value.copy(
            floorFilter = floor,
            points = if (floor == null) allPoints else allPoints.filter { it.floor == floor },
        )
    }
}
