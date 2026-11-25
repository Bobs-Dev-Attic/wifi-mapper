package com.bobsdevattic.wifimapper.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobsdevattic.wifimapper.WifiMapperApp
import com.bobsdevattic.wifimapper.capture.BleScanner
import com.bobsdevattic.wifimapper.model.ProximityEstimator
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.model.TrendDetector
import com.bobsdevattic.wifimapper.radar.RadarTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

class FinderViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BleScanner(app)
    val settings = (app as WifiMapperApp).settings

    data class UiState(
        val target: RadarTarget.Ble? = null,
        val rssi: Float? = null,
        val level: Float = 0f,
        val band: ProximityEstimator.Band = ProximityEstimator.Band.FAR,
        val peak: Float? = null,
        val belowPeak: Boolean = false,
        val trend: TrendDetector.Trend = TrendDetector.Trend.UNKNOWN,
        val secondsSinceHeard: Long? = null,
        val distanceM: Double? = null,
        val beepIntervalMs: Long = 1600,
        val matchedByName: Boolean = false,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val estimator = ProximityEstimator()
    private val trend = TrendDetector(windowMs = 2_000, thresholdDb = 3f)
    private var jobs = mutableListOf<Job>()
    private var lastProcessedMs = 0L

    fun start(target: RadarTarget.Ble) {
        stop()
        estimator.reset()
        lastProcessedMs = 0L
        _ui.value = UiState(target = target)

        // Unfiltered scan: match by address, falling back to the advertised name
        // (some devices rotate their address for privacy).
        jobs += viewModelScope.launch {
            ble.devices(staleAfterMs = 15_000) { status ->
                _ui.value = _ui.value.copy(message = when (status) {
                    BleScanner.Status.Ok -> null
                    BleScanner.Status.Off -> "Bluetooth is off. Turn it on in Quick Settings."
                    BleScanner.Status.Unsupported -> "This phone doesn't support Bluetooth LE."
                    is BleScanner.Status.Failed -> status.reason
                })
            }.collect { devices ->
                val byAddress = devices.firstOrNull { it.address.equals(target.address, ignoreCase = true) }
                val byName = if (byAddress == null && target.name.isNotBlank() && target.name != "Unknown device")
                    devices.filter { it.name == target.name }.maxByOrNull { it.rssi } else null
                val match = byAddress ?: byName ?: return@collect
                if (match.lastSeenMs <= lastProcessedMs) return@collect   // no new packet from it
                lastProcessedMs = match.lastSeenMs
                estimator.add(match.rssi, match.lastSeenMs)
                trend.add(match.lastSeenMs, match.rssi)
                publish(matchedByName = byAddress == null, txPower = match.txPower ?: target.txPower)
            }
        }
        // Tick so "last heard" and the not-heard state stay current.
        jobs += viewModelScope.launch {
            while (true) {
                delay(500)
                val now = System.currentTimeMillis()
                _ui.value = _ui.value.copy(
                    secondsSinceHeard = estimator.secondsSinceHeard(now),
                    trend = trend.trend(now),
                )
            }
        }
    }

    private fun publish(matchedByName: Boolean, txPower: Int?) {
        val now = System.currentTimeMillis()
        val s = estimator.smoothed
        _ui.value = _ui.value.copy(
            rssi = s,
            level = estimator.level(),
            band = estimator.band(),
            peak = estimator.peak,
            belowPeak = estimator.belowPeak(),
            trend = trend.trend(now),
            secondsSinceHeard = estimator.secondsSinceHeard(now),
            distanceM = s?.let { SignalDetails.estimateBleDistanceM(it.roundToInt(), txPower?.let { t -> t - 41 } ?: -59) },
            beepIntervalMs = estimator.beepIntervalMs(),
            matchedByName = matchedByName,
        )
    }

    /** Clear the "strongest so far" marker, e.g. after moving to another room. */
    fun resetPeak() { val t = _ui.value.target ?: return; start(t) }

    fun stop() { jobs.forEach { it.cancel() }; jobs.clear() }
    override fun onCleared() = stop()
}

/**
 * Hot/cold finder for a Bluetooth device: a proximity meter plus beeps and
 * vibration that speed up as you get closer, like a Geiger counter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(
    target: RadarTarget.Ble,
    onBack: () -> Unit,
    onFindDirection: (RadarTarget.Ble) -> Unit,
    vm: FinderViewModel = viewModel(),
) {
    LaunchedEffect(target) { vm.start(target) }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val palette by vm.settings.palette.collectAsStateWithLifecycle()

    var soundOn by rememberSaveableBoolean(true)
    var vibrateOn by rememberSaveableBoolean(true)
    val haptics = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }

    val heardRecently = (ui.secondsSinceHeard ?: Long.MAX_VALUE) <= 5
    LaunchedEffect(soundOn, vibrateOn) {
        while (true) {
            val s = vm.ui.value
            if ((s.secondsSinceHeard ?: Long.MAX_VALUE) <= 5) {
                if (soundOn) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                if (vibrateOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay(s.beepIntervalMs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find · ${target.name}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // Proximity ring.
            Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                val ringColor = palette.at(ui.level)
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = size.minDimension * 0.09f
                    val d = min(size.width, size.height) - stroke
                    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                    drawArc(track, 135f, 270f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                    if (heardRecently) drawArc(
                        ringColor, 135f, 270f * ui.level.coerceAtLeast(0.02f), false, tl, Size(d, d),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (heardRecently) ui.band.label else "Not heard",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (heardRecently) "${ui.rssi?.roundToInt()} dBm · ~${ui.distanceM?.let { formatDistance(it, LocalDistanceUnit.current) } ?: "?"}"
                        else ui.secondsSinceHeard?.let { "last heard ${it}s ago" } ?: "listening…",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, enabled = false, label = {
                    Text(when (ui.trend) {
                        TrendDetector.Trend.WARMER -> "▲ Warmer"
                        TrendDetector.Trend.COLDER -> "▼ Colder"
                        TrendDetector.Trend.STEADY -> "● Steady"
                        TrendDetector.Trend.UNKNOWN -> "…"
                    })
                })
                ui.peak?.let { Text("Strongest so far: ${it.roundToInt()} dBm", style = MaterialTheme.typography.bodyMedium) }
            }
            if (ui.belowPeak && heardRecently) {
                Text(
                    "Signal has dropped from its best — you may have walked past it. Turn back.",
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                )
            }
            if (ui.matchedByName) {
                Text(
                    "Matched by name (its address changed). If there are two devices with this name, " +
                        "this follows the stronger one.",
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = soundOn, onClick = { soundOn = !soundOn }, label = { Text("Beep") })
                FilterChip(selected = vibrateOn, onClick = { vibrateOn = !vibrateOn }, label = { Text("Vibrate") })
                OutlinedButton(onClick = vm::resetPeak) { Text("Reset") }
            }
            Button(onClick = { onFindDirection(target) }, modifier = Modifier.fillMaxWidth()) {
                Text("Find direction (radar sweep)")
            }

            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tips", style = MaterialTheme.typography.titleMedium)
                    Text("• Walk slowly. Beeps speed up as you get closer; follow “Warmer”.")
                    Text("• When it says Very close, check under cushions, in bags and pockets: " +
                        "fabric and bodies weaken the signal.")
                    Text("• If the signal plateaus, do a radar sweep to get a direction.")
                    Text(
                        "Can't be found if it's off, out of battery, or (for most earbuds) inside a " +
                            "closed case. This app can't make the device ring; use its maker's app for " +
                            "that (e.g. SmartThings Find for Galaxy Buds).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSaveableBoolean(initial: Boolean) =
    androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }
