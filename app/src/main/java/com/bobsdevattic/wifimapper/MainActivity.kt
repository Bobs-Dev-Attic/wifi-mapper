package com.bobsdevattic.wifimapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobsdevattic.wifimapper.radar.RadarTarget
import com.bobsdevattic.wifimapper.settings.ThemeMode
import com.bobsdevattic.wifimapper.ui.ArOverlayScreen
import com.bobsdevattic.wifimapper.ui.CaptureScreen
import com.bobsdevattic.wifimapper.ui.FindDeviceScreen
import com.bobsdevattic.wifimapper.ui.LocalDistanceUnit
import com.bobsdevattic.wifimapper.ui.FinderScreen
import com.bobsdevattic.wifimapper.ui.FloorsScreen
import com.bobsdevattic.wifimapper.ui.NetworksScreen
import com.bobsdevattic.wifimapper.ui.PermissionsScreen
import com.bobsdevattic.wifimapper.ui.RadarScreen
import com.bobsdevattic.wifimapper.ui.SessionsScreen
import com.bobsdevattic.wifimapper.ui.SettingsScreen
import com.bobsdevattic.wifimapper.ui.ViewerScreen
import com.bobsdevattic.wifimapper.ui.theme.WifiMapperTheme

/**
 * App-level navigation. Kept as a tiny sealed state (rather than pulling in
 * Navigation-Compose); [parent] defines where Back goes.
 */
sealed interface Screen {
    val parent: Screen?

    data object Sessions : Screen { override val parent: Screen? = null }
    /** [sessionId] = add a floor to that map; null = new map. */
    data class Capture(val sessionId: Long?, override val parent: Screen = Sessions) : Screen
    data class Viewer(val sessionId: Long) : Screen { override val parent: Screen = Sessions }
    data class Floors(val sessionId: Long) : Screen { override val parent: Screen get() = Viewer(sessionId) }
    data object Networks : Screen { override val parent: Screen = Sessions }
    data class Radar(val target: RadarTarget, override val parent: Screen = Networks) : Screen
    data object Settings : Screen { override val parent: Screen = Sessions }
    data object Permissions : Screen { override val parent: Screen = Settings }
    data object FindDevice : Screen { override val parent: Screen = Sessions }
    /** [sessionId] null = live paint only; otherwise can overlay that map's floors. */
    data class ArOverlay(val sessionId: Long?, override val parent: Screen = Sessions) : Screen
    data class Finder(val target: RadarTarget.Ble, override val parent: Screen = FindDevice) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as WifiMapperApp).settings
        setContent {
            val mode by settings.themeMode.collectAsStateWithLifecycle()
            val dynamic by settings.dynamicColor.collectAsStateWithLifecycle()
            val units by settings.distanceUnit.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WifiMapperTheme(darkTheme = dark, dynamicColor = dynamic) {
                CompositionLocalProvider(LocalDistanceUnit provides units) { AppRoot() }
            }
        }
    }
}

/** The finder for [t], focused on the radar sweep: Back leaves the radar for the finder. */
private fun radarFocused(t: RadarTarget.Ble, list: Screen): Screen =
    Screen.Radar(t, parent = Screen.Finder(t, parent = list))

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Sessions) }

    // System Back walks up to the parent screen instead of closing the app.
    BackHandler(enabled = screen.parent != null) { screen = screen.parent ?: Screen.Sessions }

    when (val s = screen) {
        Screen.Sessions -> SessionsScreen(
            onNewCapture = { screen = Screen.Capture(sessionId = null) },
            onOpenSession = { screen = Screen.Viewer(it) },
            onOpenNetworks = { screen = Screen.Networks },
            onOpenSettings = { screen = Screen.Settings },
            onFindDevice = { screen = Screen.FindDevice },
            onOpenAr = { screen = Screen.ArOverlay(sessionId = null) },
        )
        is Screen.Capture -> CaptureScreen(
            sessionId = s.sessionId,
            onDone = { screen = s.sessionId?.let { Screen.Viewer(it) } ?: Screen.Sessions },
            onViewSession = { screen = Screen.Viewer(it) },
        )
        is Screen.Viewer -> ViewerScreen(
            sessionId = s.sessionId,
            onBack = { screen = Screen.Sessions },
            onManageFloors = { screen = Screen.Floors(s.sessionId) },
            onOpenAr = { screen = Screen.ArOverlay(s.sessionId, parent = s) },
        )
        is Screen.Floors -> FloorsScreen(
            sessionId = s.sessionId,
            onBack = { screen = Screen.Viewer(s.sessionId) },
            onAddFloor = { screen = Screen.Capture(s.sessionId, parent = Screen.Floors(s.sessionId)) },
        )
        Screen.Networks -> NetworksScreen(
            onBack = { screen = Screen.Sessions },
            // Bluetooth devices open the hot/cold finder (radar is one tap away there).
            onLocate = { t ->
                screen = if (t is RadarTarget.Ble) Screen.Finder(t, parent = Screen.Networks) else Screen.Radar(t)
            },
            // Radar icon: Find my device, opened straight onto its radar sweep (Back → finder).
            onRadar = { t -> screen = radarFocused(t, Screen.Networks) },
        )
        is Screen.Radar -> RadarScreen(
            target = s.target,
            onBack = { screen = s.parent },
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.Sessions },
            onOpenPermissions = { screen = Screen.Permissions },
        )
        Screen.Permissions -> PermissionsScreen(onBack = { screen = Screen.Settings })
        is Screen.ArOverlay -> ArOverlayScreen(
            sessionId = s.sessionId,
            onBack = { screen = s.parent },
        )
        Screen.FindDevice -> FindDeviceScreen(
            onBack = { screen = Screen.Sessions },
            onFind = { screen = Screen.Finder(it) },
            onRadar = { screen = radarFocused(it, Screen.FindDevice) },
        )
        is Screen.Finder -> FinderScreen(
            target = s.target,
            onBack = { screen = s.parent },
            onFindDirection = { screen = Screen.Radar(it, parent = s) },
        )
    }
}
