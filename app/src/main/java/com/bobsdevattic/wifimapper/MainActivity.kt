package com.bobsdevattic.wifimapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bobsdevattic.wifimapper.ui.CaptureScreen
import com.bobsdevattic.wifimapper.ui.SessionsScreen
import com.bobsdevattic.wifimapper.ui.ViewerScreen
import com.bobsdevattic.wifimapper.ui.theme.WifiMapperTheme

/** App-level navigation targets. Kept as a tiny sealed state to avoid pulling in
 *  the full Navigation-Compose dependency for three screens. */
sealed interface Screen {
    data object Sessions : Screen
    data object Capture : Screen
    data class Viewer(val sessionId: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WifiMapperTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Sessions) }

    when (val s = screen) {
        Screen.Sessions -> SessionsScreen(
            onNewCapture = { screen = Screen.Capture },
            onOpenSession = { screen = Screen.Viewer(it) },
        )
        Screen.Capture -> CaptureScreen(
            onDone = { screen = Screen.Sessions },
            onViewSession = { screen = Screen.Viewer(it) },
        )
        is Screen.Viewer -> ViewerScreen(
            sessionId = s.sessionId,
            onBack = { screen = Screen.Sessions },
        )
    }
}
