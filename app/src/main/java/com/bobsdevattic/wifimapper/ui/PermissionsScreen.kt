package com.bobsdevattic.wifimapper.ui

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bobsdevattic.wifimapper.capture.arAvailable
import com.bobsdevattic.wifimapper.model.PermissionCatalog
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/** One line on the check page. */
private data class CheckItem(
    val title: String,
    val detail: String,
    val ok: Boolean,
    /** Essential items count as "needs attention"; optional ones are informational. */
    val essential: Boolean,
    val fixLabel: String? = null,
    val fix: (() -> Unit)? = null,
)

/** Snapshot of system switches and hardware, re-read when the screen resumes. */
private data class SystemState(
    val locationOn: Boolean,
    val wifiOn: Boolean,
    val bluetoothOn: Boolean?,          // null = no Bluetooth
    val canInstallUpdates: Boolean,
    val arCore: Boolean,
    val compass: Boolean,
    val barometer: Boolean,
)

private fun readSystem(ctx: Context): SystemState {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val bt = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    return SystemState(
        locationOn = LocationManagerCompat.isLocationEnabled(lm),
        wifiOn = wm.isWifiEnabled,
        bluetoothOn = bt?.let { runCatching { it.isEnabled }.getOrDefault(false) },
        canInstallUpdates = ctx.packageManager.canRequestPackageInstalls(),
        arCore = arAvailable(ctx),
        compass = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null,
        barometer = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
    )
}

/**
 * "Check permissions": everything the app relies on — runtime permissions,
 * system switches that silently break features, and hardware — with a fix for
 * each. Refreshes when you return from system settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val catalog = remember { PermissionCatalog.forSdk(Build.VERSION.SDK_INT) }
    val perms = rememberMultiplePermissionsState(catalog.map { it.name })   // refreshes itself on resume

    var system by remember { mutableStateOf(readSystem(ctx)) }
    LifecycleResumeEffect(Unit) {
        system = readSystem(ctx)
        onPauseOrDispose { }
    }

    fun open(intent: Intent) {
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val appSettings: () -> Unit = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }

    val permissionItems = catalog.map { p ->
        val granted = perms.permissions.firstOrNull { it.permission == p.name }?.status?.isGranted == true
        CheckItem(p.title, p.purpose, granted, p.essential,
            fixLabel = if (granted) null else "Grant",
            fix = if (granted) null else ({ perms.launchMultiplePermissionRequest() }))
    }
    val systemItems = listOfNotNull(
        CheckItem(
            "Location services", "Must be ON or Android returns empty Wi-Fi scans.",
            system.locationOn, essential = true,
            fixLabel = "Turn on", fix = { open(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        ),
        CheckItem(
            "Wi-Fi", "Needed to measure Wi-Fi signal.", system.wifiOn, essential = true,
            fixLabel = "Turn on",
            fix = {
                open(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
                     else Intent(Settings.ACTION_WIFI_SETTINGS))
            },
        ),
        system.bluetoothOn?.let { on ->
            CheckItem("Bluetooth", "Needed for Bluetooth features only.", on, essential = false,
                fixLabel = "Turn on", fix = { open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
        },
        CheckItem(
            "Install app updates", "Lets the in-app updater install new versions.",
            system.canInstallUpdates, essential = false,
            fixLabel = "Allow",
            fix = { open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))) },
        ),
    )
    val hardwareItems = listOf(
        CheckItem("ARCore (AR tracking)", if (system.arCore) "Supported" else "Not supported: mapping falls back to GPS + barometer",
            system.arCore, essential = false),
        CheckItem("Compass", if (system.compass) "Available (radar sweep)" else "Missing: the radar sweep won't work",
            system.compass, essential = false),
        CheckItem("Barometer", if (system.barometer) "Available (floor detection)" else "Missing: floors use AR height only",
            system.barometer, essential = false),
    )

    val needAttention = (permissionItems + systemItems).count { it.essential && !it.ok }
    val optionalOff = (permissionItems + systemItems).count { !it.essential && !it.ok }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check permissions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusIcon(ok = needAttention == 0, essential = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (needAttention == 0) "All set" else "$needAttention need attention",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (optionalOff == 0) "Every feature is available."
                            else "$optionalOff optional ${if (optionalOff == 1) "item is" else "items are"} off: some extras won't work.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (!perms.allPermissionsGranted) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { perms.launchMultiplePermissionRequest() }) { Text("Grant missing") }
                    OutlinedButton(onClick = appSettings) { Text("Open app settings") }
                }
                Text(
                    "If Android stops asking (after “Don't allow” twice), use Open app settings → Permissions.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("App permissions", permissionItems)
            Section("System settings", systemItems)
            Section("Hardware", hardwareItems)
        }
    }
}

@Composable
private fun Section(title: String, items: List<CheckItem>) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    items.forEach { item ->
        ListItem(
            leadingContent = { StatusIcon(item.ok, item.essential) },
            headlineContent = { Text(item.title) },
            supportingContent = {
                Column {
                    // Status in words too: never colour/icon alone.
                    Text(
                        when {
                            item.ok -> "OK"
                            item.essential -> "Needs attention"
                            else -> "Off (optional)"
                        },
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    )
                    Text(item.detail, style = MaterialTheme.typography.bodySmall)
                }
            },
            trailingContent = {
                if (!item.ok && item.fix != null) TextButton(onClick = item.fix) { Text(item.fixLabel ?: "Fix") }
            },
        )
    }
}

@Composable
private fun StatusIcon(ok: Boolean, essential: Boolean) = when {
    ok -> Icon(Icons.Default.CheckCircle, "OK", tint = MaterialTheme.colorScheme.primary)
    essential -> Icon(Icons.Default.Warning, "Needs attention", tint = MaterialTheme.colorScheme.error)
    else -> Icon(Icons.Default.Info, "Optional, off", tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
