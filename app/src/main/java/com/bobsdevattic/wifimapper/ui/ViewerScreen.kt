package com.bobsdevattic.wifimapper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.viz.CoverageStats
import com.bobsdevattic.wifimapper.viz.PointCloud3DView
import com.bobsdevattic.wifimapper.viz.SignalColor
import com.bobsdevattic.wifimapper.viz.SignalPalette
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import com.bobsdevattic.wifimapper.viz.SignalSource
import com.bobsdevattic.wifimapper.viz.ViewerViewModel
import com.bobsdevattic.wifimapper.viz.WallSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    sessionId: Long,
    onBack: () -> Unit,
    onManageFloors: () -> Unit,
    vm: ViewerViewModel = viewModel(),
) {
    LaunchedEffect(sessionId) { vm.load(sessionId) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ui.sessionName.ifBlank { "3D map" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onManageFloors) {
                        Icon(Icons.Default.Layers, contentDescription = "Floors")
                    }
                    PalettePicker(current = ui.palette, onSelect = vm::setPalette)
                    VersionBadge()
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Layer toggles + floor filter.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ColorByPicker(ui.sources, ui.colorBy, vm::setColorBy)
                FilterChip(
                    selected = ui.showWalls,
                    onClick = vm::toggleWalls,
                    label = { Text("Walls (${ui.wallSummary.count})") },
                )
                if (ui.availableFloors.size > 1) {
                    FilterChip(
                        selected = ui.floorFilter == null,
                        onClick = { vm.setFloorFilter(null) },
                        label = { Text("All floors") },
                    )
                    ui.availableFloors.forEach { f ->
                        FilterChip(
                            selected = ui.floorFilter == f.key,
                            onClick = { vm.setFloorFilter(f.key) },
                            label = { Text(f.label) },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    ui.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    ui.points.isEmpty() -> Text(
                        "No points on this floor.",
                        Modifier.align(Alignment.Center),
                    )
                    else -> PointCloud3DView(
                        points = ui.points,
                        strongest = ui.stats.strongest,
                        weakest = ui.stats.weakest,
                        walls = if (ui.showWalls) ui.walls else emptyList(),
                        markers = ui.markers,
                    )
                }
                SignalLegend(ui.palette, Modifier.align(Alignment.BottomStart).padding(16.dp))
            }

            CoverageStatsPanel(stats = ui.stats, palette = ui.palette, walls = ui.wallSummary)

            Text(
                "Drag to orbit, pinch to zoom · ▲ solid ring = strongest, ▼ dashed = weakest · grey panels = walls · ◇ = estimated transmitter location · grey points = not heard",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CoverageStatsPanel(stats: CoverageStats, palette: SignalPalette, walls: WallSummary) {
    if (stats.count == 0) return
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${stats.uniformity}%",
                    style = MaterialTheme.typography.headlineMedium,
                )
                // Score swatch in the active palette (text stays high-contrast).
                Box(
                    Modifier.width(48.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(palette.at(stats.uniformity / 100f))
                )
                Text("uniformity", style = MaterialTheme.typography.labelSmall)
                Text(stats.uniformityLabel, style = MaterialTheme.typography.labelMedium)
            }
            VerticalDivider(Modifier.height(72.dp).padding(horizontal = 16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatLine("Strongest", "${stats.strongest?.rssi ?: "—"} dBm")
                StatLine("Weakest", "${stats.weakest?.rssi ?: "—"} dBm")
                StatLine("Mean / spread", "${stats.meanRssi} dBm / ${stats.spreadDb} dB")
                StatLine("Weak zones (≤ ${CoverageStats.POOR_RSSI})", "${stats.weakZoneCount}")
                if (walls.count > 0) {
                    StatLine(
                        "Walls (measured / seen)",
                        "${walls.measured}/${walls.count}" +
                            (walls.worstLossDb?.let { " · worst −${it.toInt()} dB" } ?: ""),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SignalLegend(palette: SignalPalette, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Signal", style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier.width(140.dp).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(palette.gradient())
                )
        )
        Row(Modifier.width(140.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${SignalColor.RSSI_WORST}", style = MaterialTheme.typography.labelSmall)
            Text("${SignalColor.RSSI_BEST} dBm", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PalettePicker(current: SignalPalette, onSelect: (SignalPalette) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.Palette, contentDescription = "Colour palette")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SignalPalette.entries.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.label)
                            Text(
                                p.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p.colorblindSafe) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    leadingIcon = {
                        Box(
                            Modifier.width(36.dp).height(12.dp).clip(RoundedCornerShape(6.dp))
                                .background(Brush.horizontalGradient(p.gradient()))
                        )
                    },
                    trailingIcon = {
                        if (p == current) Icon(Icons.Default.Check, contentDescription = "Selected")
                    },
                    onClick = { onSelect(p); open = false },
                )
            }
        }
    }
}

/** "Color by" chip: strongest Wi-Fi, a specific network, or a Bluetooth device. */
@Composable
private fun ColorByPicker(sources: List<SignalSource>, current: SignalSource, onSelect: (SignalSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text("Color by: ${current.label}", maxLines = 1) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sources.groupBy { it.kind }.forEach { (kind, items) ->
                Text(
                    when (kind) {
                        SignalSource.Kind.STRONGEST_WIFI -> "Overview"
                        SignalSource.Kind.WIFI_NETWORK -> "Wi-Fi networks"
                        SignalSource.Kind.BLUETOOTH -> "Bluetooth devices"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                items.forEach { src ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(src.label)
                                if (src.kind != SignalSource.Kind.STRONGEST_WIFI) {
                                    Text("heard at ${src.pointCount} points", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        trailingIcon = { if (src.key == current.key) Icon(Icons.Default.Check, "Selected") },
                        onClick = { onSelect(src); open = false },
                    )
                }
            }
            if (sources.none { it.kind == SignalSource.Kind.BLUETOOTH }) {
                Text(
                    "No Bluetooth devices in this map. Turn on \u201cAlso record Bluetooth devices\u201d when mapping.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 280.dp),
                )
            }
        }
    }
}
