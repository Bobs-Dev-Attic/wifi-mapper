package com.bobsdevattic.wifimapper.viz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.data.SamplePointWithReadings
import com.bobsdevattic.wifimapper.model.FloorInfo
import com.bobsdevattic.wifimapper.model.FloorLayout
import com.bobsdevattic.wifimapper.model.MeasuredPoint
import com.bobsdevattic.wifimapper.model.Vector3
import com.bobsdevattic.wifimapper.model.WallLossAnalyzer
import com.bobsdevattic.wifimapper.model.WallObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A floor-filter chip: [key] is the floor's rank (0 = lowest). */
data class FloorChip(val key: Int, val label: String)

class ViewerViewModel(app: Application) : AndroidViewModel(app) {

    private val appRef = app as WifiMapperApp
    private val repo = appRef.repository
    private val settings = appRef.settings

    data class UiState(
        val sessionName: String = "",
        val points: List<CloudPoint> = emptyList(),
        val floorFilter: Int? = null,     // null = all floors; else floor rank
        val availableFloors: List<FloorChip> = emptyList(),
        val stats: CoverageStats = CoverageStats.from(emptyList()),
        val palette: SignalPalette = SignalPalette.DEFAULT,
        val walls: List<WallRender> = emptyList(),
        val wallSummary: WallSummary = WallSummary(0, 0, null),
        val showWalls: Boolean = true,
        val loading: Boolean = true,
    )

    private val _ui = MutableStateFlow(UiState(palette = settings.palette.value))
    val ui: StateFlow<UiState> = _ui

    // Raw data for the loaded map; rebuilt into render lists when settings change.
    private var floors: List<FloorInfo> = emptyList()
    private var raw: List<SamplePointWithReadings> = emptyList()
    private var rawWalls: List<Pair<WallObservation, Long?>> = emptyList()
    private var wallLosses: Map<WallObservation, com.bobsdevattic.wifimapper.model.WallLoss> = emptyMap()

    private var allPoints: List<CloudPoint> = emptyList()
    private var allWalls: List<WallRender> = emptyList()
    private var loadedSessionId: Long? = null

    init {
        // Palette and floor spacing come from Settings and apply live.
        viewModelScope.launch {
            combine(settings.palette, settings.floorSpacingM) { p, s -> p to s }.collect { (p, s) ->
                _ui.value = _ui.value.copy(palette = p)
                rebuild(p, s)
            }
        }
    }

    fun load(sessionId: Long) {
        loadedSessionId = sessionId
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, floorFilter = null)
            val session = repo.getSession(sessionId)
            floors = repo.getFloors(sessionId)
            raw = repo.getPoints(sessionId)
            rawWalls = repo.getWalls(sessionId)

            // Wall loss per floor, in each floor's own (local) frame, so points on
            // different floors are never paired across a ceiling.
            wallLosses = withContext(Dispatchers.Default) {
                val byFloor = raw.groupBy { it.point.floorId }
                rawWalls.groupBy { it.second }.flatMap { (floorId, ws) ->
                    val measured = byFloor[floorId].orEmpty().map { pwr ->
                        MeasuredPoint(
                            Vector3(pwr.point.x, pwr.point.y, pwr.point.z),
                            pwr.readings.associate { it.bssid to it.rssi },
                        )
                    }
                    WallLossAnalyzer().analyze(ws.map { it.first }, measured).map { it.wall to it }
                }.toMap()
            }

            _ui.value = _ui.value.copy(sessionName = session?.name ?: "Map $sessionId", loading = false)
            rebuild(settings.palette.value, settings.floorSpacingM.value)
        }
    }

    /** Reload after floors were edited elsewhere (labels / order). */
    fun reload() { loadedSessionId?.let(::load) }

    private fun rebuild(palette: SignalPalette, spacingM: Float) {
        val ranks = FloorLayout.ranks(floors)
        fun rankOf(floorId: Long?) = floorId?.let { ranks[it] } ?: 0
        fun lift(v: Vector3, rank: Int) = v.copy(y = v.y + FloorLayout.yOffset(rank, spacingM))

        allPoints = raw.map { pwr ->
            val rank = rankOf(pwr.point.floorId)
            CloudPoint(
                position = lift(Vector3(pwr.point.x, pwr.point.y, pwr.point.z), rank),
                color = SignalColor.forRssi(pwr.point.bestRssi, palette),
                floor = rank,
                rssi = pwr.point.bestRssi,
            )
        }
        allWalls = rawWalls.mapNotNull { (wall, floorId) ->
            val loss = wallLosses[wall] ?: return@mapNotNull null
            val rank = rankOf(floorId)
            // Render the wall lifted to its floor; keep the loss estimate.
            WallRender(loss.copy(wall = wall.copy(center = lift(wall.center, rank))), rank)
        }
        val chips = floors.map { f -> FloorChip(ranks.getValue(f.id), f.label) }.sortedBy { it.key }
        _ui.value = _ui.value.copy(availableFloors = chips)
        applyFilter(_ui.value.floorFilter)
    }

    fun setFloorFilter(floor: Int?) = applyFilter(floor)

    fun toggleWalls() { _ui.value = _ui.value.copy(showWalls = !_ui.value.showWalls) }

    /** Quick palette switch from the viewer; persisted via Settings. */
    fun setPalette(palette: SignalPalette) = settings.setPalette(palette)

    // Stats are computed from the exact list instances shown, so the MAX/MIN
    // markers (matched by reference in PointCloud3DView) stay in sync.
    private fun applyFilter(floor: Int?) {
        val shown = if (floor == null) allPoints else allPoints.filter { it.floor == floor }
        val shownWalls = if (floor == null) allWalls else allWalls.filter { it.floor == floor }
        _ui.value = _ui.value.copy(
            floorFilter = floor,
            points = shown,
            stats = CoverageStats.from(shown),
            walls = shownWalls,
            wallSummary = WallSummary.of(shownWalls),
        )
    }
}
