package com.bobsdevattic.wifimapper.ui

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.capture.ArFrame
import com.bobsdevattic.wifimapper.capture.ArOverlaySurface
import com.bobsdevattic.wifimapper.capture.WifiScanner
import com.bobsdevattic.wifimapper.capture.arAvailable
import com.bobsdevattic.wifimapper.model.ArProjector
import com.bobsdevattic.wifimapper.model.FloorInfo
import com.bobsdevattic.wifimapper.model.FloorLayout
import com.bobsdevattic.wifimapper.model.MeasuredPoint
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.model.Vector3
import com.bobsdevattic.wifimapper.model.WallLoss
import com.bobsdevattic.wifimapper.model.WallLossAnalyzer
import com.bobsdevattic.wifimapper.viz.SignalColor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A signal reading anchored in AR space. */
data class Orb(val position: Vector3, val rssi: Int)

class ArOverlayViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as WifiMapperApp).repository
    private val wifi = WifiScanner(app)
    val settings = (app as WifiMapperApp).settings

    enum class Mode { LIVE, SAVED }

    data class UiState(
        val sessionId: Long? = null,
        val mapName: String = "",
        val floors: List<FloorInfo> = emptyList(),
        val mode: Mode = Mode.LIVE,
        val floorId: Long? = null,
        val started: Boolean = false,
        val orbs: List<Orb> = emptyList(),
        val walls: List<WallLoss> = emptyList(),
        val liveRssi: Int? = null,
        val liveSource: String = "",
        val showLabels: Boolean = true,
        val loading: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** Latest camera state; separate flow so the HUD doesn't recompose every frame. */
    private val _frame = MutableStateFlow<ArFrame?>(null)
    val frame: StateFlow<ArFrame?> = _frame

    private var liveJob: Job? = null
    private var lastOrbAt: Vector3? = null

    fun prepare(sessionId: Long?) {
        stop()
        _ui.value = UiState(sessionId = sessionId, mode = if (sessionId != null) Mode.SAVED else Mode.LIVE)
        if (sessionId == null) return
        viewModelScope.launch {
            val floors = repo.getFloors(sessionId)
            _ui.value = _ui.value.copy(
                mapName = repo.getSession(sessionId)?.name ?: "Map",
                floors = FloorLayout.topDown(floors),
                floorId = FloorLayout.topDown(floors).lastOrNull()?.id,   // default: lowest floor
            )
        }
    }

    fun setMode(m: Mode) { if (!_ui.value.started) _ui.value = _ui.value.copy(mode = m) }
    fun selectFloor(id: Long) { if (!_ui.value.started) _ui.value = _ui.value.copy(floorId = id) }
    fun toggleLabels() { _ui.value = _ui.value.copy(showLabels = !_ui.value.showLabels) }

    /** Begin AR. In SAVED mode, the user is now standing at the floor's start point. */
    fun start() {
        val s = _ui.value
        _ui.value = s.copy(started = true, orbs = emptyList(), walls = emptyList())
        lastOrbAt = null
        when (s.mode) {
            Mode.LIVE -> startLive()
            Mode.SAVED -> s.sessionId?.let { sid -> s.floorId?.let { loadFloor(sid, it) } }
        }
    }

    /** Saved measurements and walls in that floor's own AR frame (origin = its start point). */
    private fun loadFloor(sessionId: Long, floorId: Long) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val points = repo.getPoints(sessionId).filter { it.point.floorId == floorId }
            val walls = repo.getWalls(sessionId).filter { it.second == floorId }.map { it.first }
            val losses = withContext(Dispatchers.Default) {
                WallLossAnalyzer().analyze(walls, points.map { pwr ->
                    MeasuredPoint(Vector3(pwr.point.x, pwr.point.y, pwr.point.z), pwr.readings.associate { it.bssid to it.rssi })
                })
            }
            _ui.value = _ui.value.copy(
                loading = false,
                orbs = points.map { Orb(Vector3(it.point.x, it.point.y, it.point.z), it.point.bestRssi) },
                walls = losses,
            )
        }
    }

    /** Live: poll the connected network's RSSI (fast); fall back to the strongest scan. */
    private fun startLive() {
        liveJob = viewModelScope.launch {
            while (true) {
                val connected = wifi.connectedRssi()
                val (rssi, label) = if (connected != null) {
                    connected to (wifi.connectedSsid()?.let { "Connected: $it" } ?: "Connected Wi-Fi")
                } else {
                    val best = wifi.detailedResults().maxByOrNull { it.rssi }
                    best?.rssi to (best?.let { "Strongest: ${it.displayName} (updates slowly)" } ?: "No Wi-Fi")
                }
                _ui.value = _ui.value.copy(liveRssi = rssi, liveSource = label)
                delay(300)
            }
        }
    }

    fun onFrame(f: ArFrame) {
        _frame.value = f
        val s = _ui.value
        if (s.mode != Mode.LIVE || !s.started || !f.tracking) return
        val rssi = s.liveRssi ?: return
        // Drop an orb slightly below the phone, every ORB_SPACING_M of movement.
        val here = f.cameraPosition.copy(y = f.cameraPosition.y - 0.15f)
        val last = lastOrbAt
        if (last == null || here.distanceTo(last) >= ORB_SPACING_M) {
            lastOrbAt = here
            _ui.value = s.copy(orbs = (s.orbs + Orb(here, rssi)).takeLast(MAX_ORBS))
        }
    }

    fun clear() { lastOrbAt = null; _ui.value = _ui.value.copy(orbs = emptyList()) }

    fun stop() { liveJob?.cancel(); liveJob = null }
    override fun onCleared() = stop()

    private companion object {
        const val ORB_SPACING_M = 0.4f
        const val MAX_ORBS = 800
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ArOverlayScreen(
    sessionId: Long?,
    onBack: () -> Unit,
    vm: ArOverlayViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) { vm.prepare(sessionId) }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val palette by vm.settings.palette.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val arOk = remember { arAvailable(context) }
    val perms = rememberMultiplePermissionsState(buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ui.mode == ArOverlayViewModel.Mode.SAVED && ui.mapName.isNotBlank()) "AR · ${ui.mapName}" else "AR signal view") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !arOk -> CenterNote("AR isn't available on this device.")
                !perms.allPermissionsGranted -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Camera and location permissions are needed for the AR view.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { perms.launchMultiplePermissionRequest() }) { Text("Grant permissions") }
                }
                !ui.started -> ArSetup(ui, vm)
                else -> {
                    val frame by vm.frame.collectAsStateWithLifecycle()
                    ArOverlaySurface(onFrame = vm::onFrame, modifier = Modifier.fillMaxSize())
                    SignalOverlay(frame, ui.orbs, ui.walls, ui.showLabels, palette, Modifier.fillMaxSize())
                    ArHud(ui, frame, palette, vm, Modifier.align(Alignment.TopCenter))
                    ArControls(ui, vm, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun ArSetup(ui: ArOverlayViewModel.UiState, vm: ArOverlayViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = ui.mode == ArOverlayViewModel.Mode.LIVE,
                onClick = { vm.setMode(ArOverlayViewModel.Mode.LIVE) },
                label = { Text("Live paint") },
            )
            FilterChip(
                selected = ui.mode == ArOverlayViewModel.Mode.SAVED,
                onClick = { vm.setMode(ArOverlayViewModel.Mode.SAVED) },
                enabled = ui.sessionId != null,
                label = { Text("Saved map") },
            )
        }
        when (ui.mode) {
            ArOverlayViewModel.Mode.LIVE -> ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Live paint", style = MaterialTheme.typography.titleMedium)
                    Text("Walk with the camera up. Every ~40 cm a glowing orb is left in the air, coloured " +
                        "by the Wi-Fi signal there. Turn around to see where coverage was strong or weak.")
                    Text("Uses your connected network (updates live).", style = MaterialTheme.typography.bodySmall)
                    if (ui.sessionId == null) Text(
                        "To overlay a saved map instead, open it in the 3D view and tap the AR button.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            ArOverlayViewModel.Mode.SAVED -> {
                Text("Floor", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.floors.forEach { f ->
                        FilterChip(selected = ui.floorId == f.id, onClick = { vm.selectFloor(f.id) }, label = { Text(f.label) })
                    }
                }
                ElevatedCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Stand at this floor's start point", style = MaterialTheme.typography.titleMedium)
                        Text("Go to the spot where you started mapping this floor and face the same direction. " +
                            "The saved measurements will then appear where you took them, with the walls " +
                            "that were detected.")
                        Text("Alignment is as good as your starting spot: expect ~0.5–1 m. Orbs show through walls.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = vm::start,
            enabled = ui.mode == ArOverlayViewModel.Mode.LIVE || ui.floorId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (ui.mode == ArOverlayViewModel.Mode.SAVED) "I'm at the start point — start AR" else "Start AR")
        }
    }
}

/** Draws world-anchored orbs (and walls) by projecting them with the AR camera matrices. */
@Composable
private fun SignalOverlay(
    frame: ArFrame?,
    orbs: List<Orb>,
    walls: List<WallLoss>,
    showLabels: Boolean,
    palette: com.bobsdevattic.wifimapper.viz.SignalPalette,
    modifier: Modifier = Modifier,
) {
    val label = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true; textSize = 34f; color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        if (!f.tracking) return@Canvas
        val w = size.width; val h = size.height

        // Walls: translucent panels with their signal-loss label.
        walls.forEach { wl ->
            val corners = wl.wall.corners().map { ArProjector.project(f.view, f.projection, it, w, h, margin = 2f) }
            if (corners.any { it == null }) return@forEach
            val path = Path().apply {
                moveTo(corners[0]!!.x, corners[0]!!.y)
                for (i in 1..3) lineTo(corners[i]!!.x, corners[i]!!.y)
                close()
            }
            drawPath(path, Color(0x33B0BEC5))
            drawPath(path, Color(0xAAECEFF1), style = Stroke(3f))
            ArProjector.project(f.view, f.projection, wl.wall.center, w, h)?.let { c ->
                val text = wl.lossDb?.let { "wall −${it.toInt()} dB" } ?: "wall"
                drawContext.canvas.nativeCanvas.drawText(text, c.x, c.y, label)
            }
        }

        // Orbs: far ones first so near ones draw on top.
        orbs.mapNotNull { o -> ArProjector.project(f.view, f.projection, o.position, w, h)?.let { it to o } }
            .sortedByDescending { it.first.distanceM }
            .forEach { (s, o) ->
                val r = ArProjector.orbRadiusPx(s.distanceM)
                val c = palette.at(SignalColor.quality(o.rssi))
                val centre = Offset(s.x, s.y)
                drawCircle(c.copy(alpha = 0.22f), r * 1.7f, centre)
                drawCircle(c.copy(alpha = 0.85f), r, centre)
                drawCircle(Color.White.copy(alpha = 0.8f), r, centre, style = Stroke(2f))
                if (showLabels && s.distanceM < 3f) {
                    drawContext.canvas.nativeCanvas.drawText("${o.rssi}", s.x, s.y - r - 10f, label)
                }
            }
    }
}

@Composable
private fun ArHud(
    ui: ArOverlayViewModel.UiState,
    frame: ArFrame?,
    palette: com.bobsdevattic.wifimapper.viz.SignalPalette,
    vm: ArOverlayViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xAA000000)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (frame?.tracking != true) {
            Text("Move the phone slowly to start tracking…", color = Color.White)
        }
        when (ui.mode) {
            ArOverlayViewModel.Mode.LIVE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.liveRssi?.let { SignalBars(it) }
                    Text(
                        ui.liveRssi?.let { "$it dBm · ${SignalDetails.qualityLabel(it)}" } ?: "No signal",
                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    )
                }
                Text(ui.liveSource, color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            ArOverlayViewModel.Mode.SAVED -> Text(
                if (ui.loading) "Loading…"
                else "${ui.orbs.size} saved points · ${ui.walls.size} walls",
                color = Color.White,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${SignalColor.RSSI_WORST}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(120.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(palette.gradient())))
            Text("${SignalColor.RSSI_BEST} dBm", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ArControls(ui: ArOverlayViewModel.UiState, vm: ArOverlayViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xAA000000)).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(selected = ui.showLabels, onClick = vm::toggleLabels, label = { Text("dBm labels") })
        if (ui.mode == ArOverlayViewModel.Mode.LIVE) {
            TextButton(onClick = vm::clear) { Text("Clear (${ui.orbs.size})", color = Color.White) }
        }
    }
}

@Composable
private fun BoxScope.CenterNote(text: String) =
    Text(text, Modifier.align(Alignment.Center).padding(24.dp))
