package com.bobsdevattic.wifimapper.ui

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.capture.BleDeviceInfo
import com.bobsdevattic.wifimapper.capture.BleScanner
import com.bobsdevattic.wifimapper.capture.WifiNetworkInfo
import com.bobsdevattic.wifimapper.capture.WifiScanner
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.radar.RadarTarget
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NetworksViewModel(app: Application) : AndroidViewModel(app) {
    private val wifi = WifiScanner(app)
    private val ble = BleScanner(app)

    val wifiNetworks: StateFlow<List<WifiNetworkInfo>> = wifi.detailedScanResults()
        .map { list -> list.sortedWith(compareByDescending<WifiNetworkInfo> { it.connected }.thenByDescending { it.rssi }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _bleStatus = MutableStateFlow<BleScanner.Status?>(null)
    val bleStatus: StateFlow<BleScanner.Status?> = _bleStatus

    /** Collected only while the Bluetooth tab is visible, so BLE scans stop when you leave. */
    val bleDevices: StateFlow<List<BleDeviceInfo>> = ble.devices { _bleStatus.value = it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    /** Ask for a fresh Wi-Fi scan. Returns false if Android throttled it. */
    fun rescanWifi(): Boolean = wifi.requestScan()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun NetworksScreen(
    onBack: () -> Unit,
    onLocate: (RadarTarget) -> Unit,
    vm: NetworksViewModel = viewModel(),
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby signals") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    TextButton(onClick = { onLocate(RadarTarget.ConnectedWifi) }) {
                        Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(4.dp)); Text("Find my router")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Wi-Fi") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bluetooth") })
            }
            when (tab) {
                0 -> {
                    val perms = rememberMultiplePermissionsState(buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    })
                    if (!perms.allPermissionsGranted) PermissionNeeded(
                        "Location and nearby-Wi-Fi permissions are needed to read Wi-Fi scans.",
                    ) { perms.launchMultiplePermissionRequest() }
                    else WifiTab(vm, onLocate)
                }
                else -> {
                    val perms = rememberMultiplePermissionsState(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
                        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
                    )
                    if (!perms.allPermissionsGranted) PermissionNeeded(
                        "Bluetooth scan permission is needed to list nearby Bluetooth LE devices.",
                    ) { perms.launchMultiplePermissionRequest() }
                    else BleTab(vm, onLocate)
                }
            }
        }
    }
}

@Composable
private fun WifiTab(vm: NetworksViewModel, onLocate: (RadarTarget) -> Unit) {
    val networks by vm.wifiNetworks.collectAsStateWithLifecycle()
    var band by rememberSaveable { mutableStateOf<String?>(null) }
    var throttled by remember { mutableStateOf(false) }
    val shown = networks.filter { band == null || it.band == band }

    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(null, "2.4 GHz", "5 GHz", "6 GHz").forEach { b ->
            FilterChip(selected = band == b, onClick = { band = b }, label = { Text(b ?: "All") })
        }
        IconButton(onClick = { throttled = !vm.rescanWifi() }) { Icon(Icons.Default.Refresh, "Rescan") }
    }
    if (throttled) {
        Text(
            "Android limits Wi-Fi scans to 4 per 2 minutes, so this list may be up to ~30 s old. " +
                "To lift the limit: Developer options → Wi-Fi scan throttling → off.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    Text(
        "${shown.size} networks",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.bssid }) { n ->
            ListItem(
                leadingContent = { SignalBars(n.rssi) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.displayName, fontWeight = if (n.connected) FontWeight.Bold else null)
                        if (n.connected) {
                            Spacer(Modifier.width(6.dp))
                            AssistChip(onClick = {}, enabled = false, label = { Text("connected") })
                        }
                    }
                },
                supportingContent = {
                    Column {
                        Text(
                            "${n.rssi} dBm · ${SignalDetails.qualityLabel(n.rssi)} · " +
                                "${n.band} ch ${n.channel ?: "?"} · ${n.width}",
                        )
                        Text(listOfNotNull(n.standard, n.security, n.distanceRangeText).joinToString(" · "))
                        Text(n.bssid + if (n.ageSeconds > 20) " · seen ${n.ageSeconds}s ago" else "",
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                },
                trailingContent = {
                    IconButton(onClick = {
                        onLocate(
                            if (n.connected) RadarTarget.ConnectedWifi
                            else RadarTarget.Wifi(n.bssid, n.ssid, n.frequencyMhz)
                        )
                    }) { Icon(Icons.Default.MyLocation, "Locate with radar") }
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun BleTab(vm: NetworksViewModel, onLocate: (RadarTarget) -> Unit) {
    val devices by vm.bleDevices.collectAsStateWithLifecycle()
    val status by vm.bleStatus.collectAsStateWithLifecycle()
    var namedOnly by rememberSaveable { mutableStateOf(false) }
    val shown = devices.filter { !namedOnly || it.name != null }

    when (val s = status) {
        BleScanner.Status.Off -> { Notice("Bluetooth is off. Turn it on in Quick Settings, then reopen this tab."); return }
        BleScanner.Status.Unsupported -> { Notice("This phone doesn't support Bluetooth LE."); return }
        is BleScanner.Status.Failed -> Notice(s.reason)
        else -> {}
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = namedOnly, onClick = { namedOnly = !namedOnly }, label = { Text("Named only") })
        Text("${shown.size} devices · live", style = MaterialTheme.typography.labelMedium)
    }
    Text(
        "Bluetooth LE devices (earbuds, watches, trackers, smart-home gear). Most phones " +
            "randomise their address, so the same device can appear more than once.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyColumn(Modifier.fillMaxSize()) {
        items(shown, key = { it.address }) { d ->
            ListItem(
                leadingContent = { SignalBars(d.rssi) },
                headlineContent = { Text(d.displayName) },
                supportingContent = {
                    Column {
                        Text("${d.rssi} dBm · ${SignalDetails.qualityLabel(d.rssi)} · ~${formatDistance(d.approxDistanceM)}")
                        Text(listOfNotNull(
                            d.manufacturer,
                            d.txPower?.let { "TX $it dBm" },
                            if (d.connectable) "connectable" else "beacon",
                        ).joinToString(" · "))
                        Text(d.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onLocate(RadarTarget.Ble(d.address, d.displayName, d.txPower)) }) {
                        Icon(Icons.Default.MyLocation, "Locate with radar")
                    }
                },
            )
            HorizontalDivider()
        }
    }
}

/** Four-bar signal indicator drawn directly (no colour-only meaning). */
@Composable
fun SignalBars(rssi: Int, modifier: Modifier = Modifier) {
    val bars = SignalDetails.bars(rssi)
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(28.dp, 22.dp)) {
        val w = size.width / 4f
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4f
            drawRoundRect(
                color = if (i < bars) on else off,
                topLeft = Offset(i * w + w * 0.15f, size.height - h),
                size = Size(w * 0.7f, h),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
    }
}

fun formatDistance(m: Double): String = when {
    m < 1 -> "<1 m"
    m < 10 -> "%.1f m".format(m)
    m < 100 -> "${m.toInt()} m"
    else -> ">100 m"
}

@Composable
private fun Notice(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))

@Composable
private fun PermissionNeeded(text: String, onGrant: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrant) { Text("Grant permission") }
    }
}
