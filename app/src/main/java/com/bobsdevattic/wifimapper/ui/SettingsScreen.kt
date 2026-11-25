package com.bobsdevattic.wifimapper.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobsdevattic.wifimapper.BuildConfig
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.model.DistanceUnit
import com.bobsdevattic.wifimapper.model.UnitFormat
import com.bobsdevattic.wifimapper.settings.AppSettings
import com.bobsdevattic.wifimapper.settings.RadarStyle
import com.bobsdevattic.wifimapper.settings.ThemeMode
import com.bobsdevattic.wifimapper.viz.SignalPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPermissions: () -> Unit) {
    val settings = (LocalContext.current.applicationContext as WifiMapperApp).settings
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val dynamic by settings.dynamicColor.collectAsStateWithLifecycle()
    val palette by settings.palette.collectAsStateWithLifecycle()
    val spacing by settings.floorSpacingM.collectAsStateWithLifecycle()
    val radarStyle by settings.radarStyle.collectAsStateWithLifecycle()
    val pingSound by settings.pingSound.collectAsStateWithLifecycle()
    val units by settings.distanceUnit.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(Icons.Default.VerifiedUser, null) },
                    headlineContent = { Text("Check permissions") },
                    supportingContent = { Text("Confirm what the app is allowed to use, and fix anything missing") },
                    modifier = Modifier.clickable(onClick = onOpenPermissions),
                )
            }

            SectionTitle("App theme")
            ThemeMode.entries.forEach { m ->
                RadioRow(selected = themeMode == m, onClick = { settings.setThemeMode(m) }) { Text(m.label) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Material You colours")
                        Text("Match your wallpaper (One UI 7 / Android 12+)",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = dynamic, onCheckedChange = settings::setDynamicColor)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Units")
            DistanceUnit.entries.forEach { u ->
                RadioRow(selected = units == u, onClick = { settings.setDistanceUnit(u) }) {
                    Column {
                        Text(u.label)
                        Text(
                            if (u == DistanceUnit.METRIC) "e.g. 5.9-22 m, tracker rings 1/3/10/30 m"
                            else "e.g. 19-72 ft, tracker rings 3/10/30/100 ft",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Signal colours")
            Text(
                "Used for points, the legend and the uniformity score. All except " +
                    "Red → Green stay readable with colour blindness and in greyscale.",
                style = MaterialTheme.typography.bodySmall,
            )
            SignalPalette.entries.forEach { p ->
                RadioRow(selected = palette == p, onClick = { settings.setPalette(p) }) {
                    Column(Modifier.weight(1f)) {
                        Text(p.label)
                        Text(
                            p.description, style = MaterialTheme.typography.bodySmall,
                            color = if (p.colorblindSafe) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error,
                        )
                    }
                    Box(
                        Modifier.width(72.dp).height(14.dp).clip(RoundedCornerShape(7.dp))
                            .background(Brush.horizontalGradient(p.gradient()))
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Radar")
            RadarStyle.entries.forEach { st ->
                RadioRow(selected = radarStyle == st, onClick = { settings.setRadarStyle(st) }) { Text(st.label) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Tracker sounds")
                    Text("Ping as the wave goes out, bleep when it reaches the target (higher = closer)",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = pingSound, onCheckedChange = settings::setPingSound)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("3D view")
            Text("Floor spacing: ${UnitFormat.length(spacing.toDouble(), units)}")
            Slider(
                value = spacing,
                onValueChange = { settings.setFloorSpacingM((it * 2).roundToInt() / 2f) },  // 0.5 m steps
                valueRange = AppSettings.MIN_SPACING_M..AppSettings.MAX_SPACING_M,
            )
            Text("Vertical gap between stacked floors. Larger values separate floors more clearly.",
                style = MaterialTheme.typography.bodySmall)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("About")
            Text("WiFi Mapper", style = MaterialTheme.typography.titleLarge)
            Text("Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyLarge)
            Text("Updates are checked automatically on launch.", style = MaterialTheme.typography.bodySmall)
            val ctx = LocalContext.current
            OutlinedButton(onClick = {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}/blob/main/CHANGELOG.md"),
                        )
                    )
                }
            }) { Text("View changelog") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun RadioRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        content()
    }
}
