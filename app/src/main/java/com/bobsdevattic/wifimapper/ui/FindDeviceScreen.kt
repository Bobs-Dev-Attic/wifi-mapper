package com.bobsdevattic.wifimapper.ui

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.capture.BleDeviceInfo
import com.bobsdevattic.wifimapper.capture.BleScanner
import com.bobsdevattic.wifimapper.capture.PairedDevice
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.radar.RadarTarget
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FindDeviceViewModel(app: Application) : AndroidViewModel(app) {
    private val ble = BleScanner(app)

    private val _paired = MutableStateFlow<List<PairedDevice>?>(emptyList())
    /** null = permission to read paired devices is missing. */
    val paired: StateFlow<List<PairedDevice>?> = _paired

    private val _status = MutableStateFlow<BleScanner.Status?>(null)
    val status: StateFlow<BleScanner.Status?> = _status

    val nearby: StateFlow<List<BleDeviceInfo>> = ble.devices(staleAfterMs = 20_000) { _status.value = it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    fun refreshPaired() { _paired.value = ble.pairedDevices() }
}

/** Is [paired] among the advertising devices? Matched by address, then by name. */
fun findHeard(paired: PairedDevice, nearby: List<BleDeviceInfo>): BleDeviceInfo? =
    nearby.firstOrNull { it.address.equals(paired.address, ignoreCase = true) }
        ?: nearby.filter { it.name != null && it.name == paired.name }.maxByOrNull { it.rssi }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun FindDeviceScreen(
    onBack: () -> Unit,
    onFind: (RadarTarget.Ble) -> Unit,
    vm: FindDeviceViewModel = viewModel(),
) {
    val perms = rememberMultiplePermissionsState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find my device") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (perms.allPermissionsGranted) IconButton(onClick = vm::refreshPaired) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!perms.allPermissionsGranted) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Bluetooth permissions are needed to list your paired devices and hear " +
                            "nearby ones.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { perms.launchMultiplePermissionRequest() }) { Text("Grant permission") }
                }
            } else {
                DeviceLists(vm, onFind)
            }
        }
    }
}

@Composable
private fun DeviceLists(vm: FindDeviceViewModel, onFind: (RadarTarget.Ble) -> Unit) {
    LaunchedEffect(Unit) { vm.refreshPaired() }
    val paired by vm.paired.collectAsStateWithLifecycle()
    val nearby by vm.nearby.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize()) {
        when (status) {
            BleScanner.Status.Off -> item { Hint("Bluetooth is off. Turn it on in Quick Settings to search.") }
            BleScanner.Status.Unsupported -> item { Hint("This phone doesn't support Bluetooth LE.") }
            is BleScanner.Status.Failed -> item { Hint((status as BleScanner.Status.Failed).reason) }
            else -> {}
        }

        item { SectionHeader("Your paired devices") }
        val p = paired
        when {
            p == null -> item { Hint("Grant the Bluetooth permission to see paired devices.") }
            p.isEmpty() -> item { Hint("No paired devices.") }
            else -> items(p, key = { "p-" + it.address }) { d ->
                val heard = findHeard(d, nearby)
                ListItem(
                    leadingContent = { if (heard != null) SignalBars(heard.rssi) else Spacer(Modifier.size(28.dp, 22.dp)) },
                    headlineContent = { Text(d.name) },
                    supportingContent = {
                        Text(
                            when {
                                heard != null -> "Heard now · ${heard.rssi} dBm · ~${formatDistance(heard.approxDistanceM, LocalDistanceUnit.current)}"
                                !d.supportsLe -> "Classic Bluetooth only: can't be tracked by signal while it's not connected"
                                else -> "Not heard right now (off, out of range, or in its case)"
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        onFind(RadarTarget.Ble(heard?.address ?: d.address, d.name, heard?.txPower))
                    },
                )
                HorizontalDivider()
            }
        }

        item { SectionHeader("Nearby now") }
        val others = nearby.sortedWith(compareBy<BleDeviceInfo> { it.name == null }.thenByDescending { it.rssi }).take(40)
        if (others.isEmpty()) item { Hint("Listening for Bluetooth devices…") }
        items(others, key = { "n-" + it.address }) { d ->
            ListItem(
                leadingContent = { SignalBars(d.rssi) },
                headlineContent = { Text(d.displayName) },
                supportingContent = {
                    Text(listOfNotNull("${d.rssi} dBm", SignalDetails.qualityLabel(d.rssi), "~${formatDistance(d.approxDistanceM, LocalDistanceUnit.current)}")
                        .joinToString(" · "))
                },
                modifier = Modifier.clickable { onFind(RadarTarget.Ble(d.address, d.displayName, d.txPower)) },
            )
            HorizontalDivider()
        }
        item {
            Hint(
                "Tap a device to start the hot/cold finder. Lost earbuds usually need to be out " +
                    "of their case to be heard.",
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
)

@Composable
private fun Hint(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
)
