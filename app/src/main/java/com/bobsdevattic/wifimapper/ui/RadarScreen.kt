package com.bobsdevattic.wifimapper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.model.RadarAccumulator
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.model.TrendDetector
import com.bobsdevattic.wifimapper.radar.RadarTarget
import com.bobsdevattic.wifimapper.radar.RadarViewModel
import com.bobsdevattic.wifimapper.viz.SignalPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Signal radar: a compass-oriented polar plot built from a body-shadow sweep,
 * an arrow toward the strongest direction, and a warmer/colder trend for
 * walking toward the source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    target: RadarTarget,
    onBack: () -> Unit,
    vm: RadarViewModel = viewModel(),
) {
    LaunchedEffect(target) { vm.start(target) }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val palette by vm.settings.palette.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radar · ${ui.target.title}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Live reading + trend.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ui.rssi?.let { SignalBars(it, Modifier.size(36.dp, 28.dp)) }
                Text(
                    ui.rssi?.let { "$it dBm" } ?: "waiting…",
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                )
                TrendChip(ui.trend)
            }
            Text(
                listOfNotNull(
                    ui.rssi?.let(SignalDetails::qualityLabel),
                    ui.distanceM?.let { "roughly ${formatDistance(it)} away" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (ui.slowUpdates) {
                Text(
                    "This network only updates when Android allows a Wi-Fi scan (~every 30 s), " +
                        "too slow for a sweep. For a live radar: connect to it, or turn off " +
                        "Developer options → Wi-Fi scan throttling. Bluetooth devices update live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!ui.hasCompass) {
                Text("No compass sensor: the sweep needs one. The warmer/colder trend still works.",
                    color = MaterialTheme.colorScheme.error)
            }

            RadarPlot(ui, palette, Modifier.fillMaxWidth().aspectRatio(1f))

            DirectionText(ui)

            if (ui.sweeping) {
                LinearProgressIndicator(progress = { ui.coverage }, modifier = Modifier.fillMaxWidth())
                Text("Circle covered: ${(ui.coverage * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = vm::stopSweep, modifier = Modifier.fillMaxWidth()) { Text("Stop sweep") }
            } else {
                Button(onClick = vm::startSweep, enabled = ui.hasCompass, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.coverage > 0f) "New sweep" else "Start sweep")
                }
            }

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How to find the source", style = MaterialTheme.typography.titleMedium)
                    Text("1. Hold the phone upright at chest height, screen facing you.")
                    Text("2. Tap Start sweep and turn slowly on the spot through a full circle (about 20 s). " +
                        "Your body blocks signal from behind, so it's strongest when you face the source.")
                    Text("3. Walk the way the arrow points. Watch the trend: Warmer means you're getting closer.")
                    Text("4. Repeat the sweep as you go. Near the source the arrow can swing: it may be " +
                        "above or below you (another floor).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TrendChip(trend: TrendDetector.Trend) {
    val (text, sym) = when (trend) {
        TrendDetector.Trend.WARMER -> "Warmer" to "▲"
        TrendDetector.Trend.COLDER -> "Colder" to "▼"
        TrendDetector.Trend.STEADY -> "Steady" to "●"
        TrendDetector.Trend.UNKNOWN -> "…" to ""
    }
    AssistChip(onClick = {}, enabled = false, label = { Text("$sym $text".trim()) })
}

@Composable
private fun DirectionText(ui: RadarViewModel.UiState) {
    val bearing = ui.bearingDeg
    val text = when {
        bearing == null && ui.sweeping -> "Keep turning slowly…"
        bearing == null -> "Do a sweep to find the direction."
        ui.contrastDb < 3f -> "No clear direction (only ${"%.1f".format(ui.contrastDb)} dB difference). " +
            "You may be very close, or the source is above/below you."
        else -> {
            val rel = ((bearing - ui.headingDeg + 540f) % 360f) - 180f   // −180..180, + = right
            val turn = when {
                kotlin.math.abs(rel) <= 15f -> "Straight ahead"
                rel > 0 -> "Turn ${rel.roundToInt()}° right"
                else -> "Turn ${(-rel).roundToInt()}° left"
            }
            "$turn · strongest toward ${RadarAccumulator.compassPoint(bearing)} (${bearing.roundToInt()}°) · " +
                "${"%.0f".format(ui.contrastDb)} dB stronger than the weakest side"
        }
    }
    Text(text, style = MaterialTheme.typography.titleSmall)
}

/**
 * Polar plot rotated so the top is the way you're facing. Each wedge is one 10°
 * sector: its length and colour show how strong the signal was facing that way.
 */
@Composable
private fun RadarPlot(ui: RadarViewModel.UiState, palette: SignalPalette, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val textPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textSize = 34f; textAlign = android.graphics.Paint.Align.CENTER }
    }
    textPaint.color = android.graphics.Color.argb(
        (onSurface.alpha * 255).toInt(), (onSurface.red * 255).toInt(),
        (onSurface.green * 255).toInt(), (onSurface.blue * 255).toInt(),
    )

    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = min(size.width, size.height) / 2 * 0.86f

        // Screen angle for a compass bearing: the user's heading points up.
        fun pt(bearingDeg: Float, radius: Float): Offset {
            val a = Math.toRadians((bearingDeg - ui.headingDeg).toDouble())
            return Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
        }

        for (k in 1..4) drawCircle(ring, r * k / 4, c, style = Stroke(1.5f))

        // Sector wedges, normalised between the weakest and strongest sectors.
        val means = ui.sectorMeans
        val filled = means.filterNotNull()
        val lo = filled.minOrNull() ?: 0f
        val hi = filled.maxOrNull() ?: 0f
        val span = (hi - lo).coerceAtLeast(1f)
        val w = 360f / means.size
        means.forEachIndexed { i, m ->
            val start = i * w
            if (m == null) return@forEachIndexed
            val t = (m - lo) / span
            val len = r * (0.25f + 0.75f * t)
            val path = Path().apply {
                moveTo(c.x, c.y)
                val steps = 4
                for (s in 0..steps) {
                    val p = pt(start + w * s / steps, len); lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, palette.at(t).copy(alpha = 0.9f))
        }

        // North marker.
        val n = pt(0f, r + 26f)
        drawContext.canvas.nativeCanvas.drawText("N", n.x, n.y + 12f, textPaint)

        // "You're facing" tick at the top.
        drawLine(onSurface, Offset(c.x, c.y - r - 4f), Offset(c.x, c.y - r + 18f), strokeWidth = 4f)

        // Arrow toward the strongest direction.
        ui.bearingDeg?.takeIf { ui.contrastDb >= 3f }?.let { b ->
            val tip = pt(b, r * 0.95f)
            drawLine(Color.White, c, tip, strokeWidth = 14f)
            drawLine(accent, c, tip, strokeWidth = 8f)
            drawCircle(accent, 14f, tip)
            drawCircle(Color.White, 14f, tip, style = Stroke(3f))
        }
        drawCircle(onSurface, 6f, c)
    }
}
