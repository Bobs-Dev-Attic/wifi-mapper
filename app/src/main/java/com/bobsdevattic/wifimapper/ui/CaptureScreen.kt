package com.bobsdevattic.wifimapper.ui

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.capture.ArCaptureSurface
import com.bobsdevattic.wifimapper.capture.CaptureViewModel
import com.bobsdevattic.wifimapper.capture.arAvailable
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    onDone: () -> Unit,
    onViewSession: (Long) -> Unit,
    vm: CaptureViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

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

    var showNameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ui.sessionId == null) "New map" else ui.sessionName) },
                navigationIcon = {
                    TextButton(onClick = {
                        vm.stopSession(); onDone()
                    }) { Text("Done") }
                },
                actions = { VersionBadge() },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            // Camera / AR background while a session is active and AR is usable.
            if (ui.sessionId != null && arOk && permState.allPermissionsGranted) {
                ArCaptureSurface(
                    poseSource = vm.poseSource,
                    wallSource = vm.wallSource,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (!permState.allPermissionsGranted) {
                PermissionPrompt(
                    onGrant = { permState.launchMultiplePermissionRequest() },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                StatusOverlay(
                    ui = ui,
                    arOk = arOk,
                    onStart = { showNameDialog = true },
                    onCapture = vm::recordSample,
                    onToggleAuto = vm::toggleAutoCapture,
                    onView = { ui.sessionId?.let(onViewSession) },
                )
            }
        }
    }

    if (showNameDialog) {
        SessionNameDialog(
            onConfirm = { name -> vm.startSession(name); showNameDialog = false },
            onDismiss = { showNameDialog = false },
        )
    }
}

@Composable
private fun StatusOverlay(
    ui: CaptureViewModel.UiState,
    arOk: Boolean,
    onStart: () -> Unit,
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
                StatRow("Floor", ui.floor.toString(), null)
                StatRow("Visible APs", ui.visibleAps.toString(), null)
                StatRow("Best RSSI", ui.bestRssi?.let { "$it dBm" } ?: "—", null)
                StatRow("Points", ui.pointCount.toString(), null)
                StatRow("GPS anchor", if (ui.anchorSet) "set" else "waiting…", null)
                StatRow("Walls seen", ui.wallsDetected.toString(), null)
                if (!ui.hasBarometer) {
                    Text("No barometer: floor from AR height only.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(ui.status, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.weight(1f))

        if (ui.sessionId == null) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                Text("Start mapping")
            }
        } else {
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

@Composable
private fun SessionNameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this map") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                singleLine = true, label = { Text("e.g. Home – all floors") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.ifBlank { "Untitled map" }) },
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
