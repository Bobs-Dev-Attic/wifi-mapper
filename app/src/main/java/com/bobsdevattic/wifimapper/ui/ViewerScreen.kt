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
import com.bobsdevattic.wifimapper.viz.PointCloud3DView
import com.bobsdevattic.wifimapper.viz.SignalColor
import com.bobsdevattic.wifimapper.viz.ViewerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    sessionId: Long,
    onBack: () -> Unit,
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
                actions = { VersionBadge() },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Floor filter.
            if (ui.availableFloors.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ui.floorFilter == null,
                        onClick = { vm.setFloorFilter(null) },
                        label = { Text("All floors") },
                    )
                    ui.availableFloors.forEach { f ->
                        FilterChip(
                            selected = ui.floorFilter == f,
                            onClick = { vm.setFloorFilter(f) },
                            label = { Text("Floor $f") },
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
                    else -> PointCloud3DView(points = ui.points)
                }
                SignalLegend(Modifier.align(Alignment.BottomStart).padding(16.dp))
            }

            Text(
                "${ui.points.size} points · drag to orbit, pinch to zoom",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun SignalLegend(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Signal", style = MaterialTheme.typography.labelSmall)
        Box(
            Modifier.width(140.dp).height(10.dp).clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            SignalColor.forRssi(SignalColor.RSSI_WORST),
                            SignalColor.forRssi((SignalColor.RSSI_WORST + SignalColor.RSSI_BEST) / 2),
                            SignalColor.forRssi(SignalColor.RSSI_BEST),
                        )
                    )
                )
        )
        Row(Modifier.width(140.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${SignalColor.RSSI_WORST}", style = MaterialTheme.typography.labelSmall)
            Text("${SignalColor.RSSI_BEST} dBm", style = MaterialTheme.typography.labelSmall)
        }
    }
}
