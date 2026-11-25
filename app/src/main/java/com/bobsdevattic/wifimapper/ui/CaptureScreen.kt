package com.bobsdevattic.wifimapper.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.capture.ArCaptureSurface
import com.bobsdevattic.wifimapper.capture.CaptureViewModel
import com.bobsdevattic.wifimapper.capture.arAvailable
import com.bobsdevattic.wifimapper.model.FloorLayout
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * Capture flow:
 *  1. Set-up: name the map/floor and stand at the floor's start point (a spot you
 *     can find on every floor, facing a fixed direction). The AR session starts
 *     there, so its origin — and therefore every floor — lines up.
 *  2. Capture: walk the floor; points auto-drop as you move.
 *
 * @param sessionId add a floor to this map, or null for a new map
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    sessionId: Long?,
    onDone: () -> Unit,
    onViewSession: (Long) -> Unit,
    vm: CaptureViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { vm.prepare(sessionId) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permState = rememberMultiplePermissionsState(permissions)
    val context = androidx.compose.ui.platform.LocalContext.current
    val arOk = remember { arAvailable(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            ui.sessionId != null -> "${ui.sessionName} · ${ui.floorLabel}"
                            sessionId != null -> "Add floor"
                            else -> "New map"
                        }
                    )
                },
                navigationIcon = {
                    TextButton(onClick = { vm.stopSession(); onDone() }) {
                        Text(if (ui.sessionId == null) "Cancel" else "Done")
                    }
                },
                actions = { VersionBadge() },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            // Camera / AR background while a floor is being captured.
            if (ui.sessionId != null && arOk && permState.allPermissionsGranted) {
                ArCaptureSurface(
                    poseSource = vm.poseSource,
                    wallSource = vm.wallSource,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            when {
                !permState.allPermissionsGranted -> PermissionPrompt(
                    onGrant = { permState.launchMultiplePermissionRequest() },
                    modifier = Modifier.align(Alignment.Center),
                )
                ui.sessionId == null -> SetupStep(
                    ui = ui,
                    addingToExisting = sessionId != null,
                    onStart = { name, label, above, ble -> vm.startCapture(name, label, above, ble) },
                )
                else -> StatusOverlay(
                    ui = ui,
                    arOk = arOk,
                    onCapture = vm::recordSample,
                    onToggleAuto = vm::toggleAutoCapture,
                    onView = { ui.sessionId?.let(onViewSession) },
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SetupStep(
    ui: CaptureViewModel.UiState,
    addingToExisting: Boolean,
    onStart: (mapName: String, floorLabel: String, above: Boolean, recordBle: Boolean) -> Unit,
) {
    val blePerms = rememberMultiplePermissionsState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_SCAN)
        else emptyList()
    )
    var recordBle by remember { mutableStateOf(true) }
    val existing = ui.existingFloors
    var mapName by remember { mutableStateOf("") }
    var floorLabel by remember(existing.size, addingToExisting) {
        mutableStateOf(if (addingToExisting) "Floor ${existing.size + 1}" else "Ground floor")
    }
    var above by remember { mutableStateOf(true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!addingToExisting) {
            OutlinedTextField(
                value = mapName, onValueChange = { mapName = it },
                label = { Text("Map name (e.g. Home)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Adding a floor to “${ui.targetMapName}”", style = MaterialTheme.typography.titleMedium)
            if (existing.isNotEmpty()) {
                Text(
                    "Existing floors (top to bottom): " +
                        FloorLayout.topDown(existing).joinToString(" › ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedTextField(
            value = floorLabel, onValueChange = { floorLabel = it },
            label = { Text("Floor label (e.g. Ground, Upstairs, Basement)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (addingToExisting && existing.isNotEmpty()) {
            Column {
                Text("Where is this floor?", style = MaterialTheme.typography.labelLarge)
                listOf(true to "Above the top floor", false to "Below the bottom floor").forEach { (value, text) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = above == value, onClick = { above = value }, role = Role.RadioButton)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = above == value, onClick = null)
                        Spacer(Modifier.width(8.dp)); Text(text)
                    }
                }
                Text("You can relabel and reorder floors later.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Also record Bluetooth devices")
                Text(
                    if (recordBle && !blePerms.allPermissionsGranted) "Needs the Bluetooth permission \u2014 tap to grant"
                    else "Lets you colour the 3D map by a Bluetooth device and estimate where it is.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = recordBle,
                onCheckedChange = { on ->
                    recordBle = on
                    if (on && !blePerms.allPermissionsGranted) blePerms.launchMultiplePermissionRequest()
                },
            )
        }

        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null); Spacer(Modifier.width(8.dp))
                    Text("Mark the start point", style = MaterialTheme.typography.titleMedium)
                }
                if (!addingToExisting) {
                    Text(
                        "Pick a spot you can find again on every floor, ideally stacked " +
                            "vertically: e.g. the corner by the stairs, or under a skylight. " +
                            "Stand there, facing a fixed direction (e.g. the front-door wall).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "Stand directly above/below the start point you used on the other " +
                            "floors, facing the SAME direction. This lines the floors up in 3D.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Hold the phone at chest height. Mapping starts from exactly where you stand.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = {
                onStart(
                    mapName.ifBlank { "Untitled map" }, floorLabel.ifBlank { "Floor" }, above,
                    recordBle && blePerms.allPermissionsGranted,
                )
            },
            enabled = addingToExisting.not() || ui.targetSessionId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("I'm at the start point — start mapping") }
    }
}

@Composable
private fun StatusOverlay(
    ui: CaptureViewModel.UiState,
    arOk: Boolean,
    onCapture: () -> Unit,
    onToggleAuto: () -> Unit,
    onView: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ElevatedCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!arOk) {
                    Text(
                        "ARCore unavailable on this device — using GPS + barometer only. " +
                            "Positions will be coarser; use manual capture.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                StatRow("Tracking", if (ui.tracking) "AR locked" else "not tracking",
                    if (ui.tracking) Color(0xFF1565C0) else Color(0xFFE65100))
                StatRow("Floor", ui.floorLabel, null)
                StatRow("Visible APs", ui.visibleAps.toString(), null)
                StatRow("Best RSSI", ui.bestRssi?.let { "$it dBm" } ?: "—", null)
                StatRow("Points", ui.pointCount.toString(), null)
                StatRow("GPS anchor", if (ui.anchorSet) "set" else "waiting…", null)
                StatRow("Walls seen", ui.wallsDetected.toString(), null)
                StatRow("Bluetooth devices", if (ui.recordingBle) ui.bleDevicesNearby.toString() else "not recorded", null)
                Text(ui.status, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = ui.autoCapture,
                onClick = onToggleAuto,
                label = { Text("Auto-capture") },
            )
            if (ui.pointCount > 0) {
                AssistChip(onClick = onView, label = { Text("View 3D map") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp))
            Text("Capture point here")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "WiFi Mapper needs camera (for AR tracking), location and nearby-WiFi " +
                "permissions to measure and place signal readings.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onGrant) { Text("Grant permissions") }
    }
}
