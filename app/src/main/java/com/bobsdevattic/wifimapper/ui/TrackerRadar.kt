package com.bobsdevattic.wifimapper.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobsdevattic.wifimapper.model.CompassMath
import com.bobsdevattic.wifimapper.model.RadarAccumulator
import com.bobsdevattic.wifimapper.model.SectorShading
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.model.TrackerMath
import com.bobsdevattic.wifimapper.model.UnitFormat
import com.bobsdevattic.wifimapper.radar.TrackerAudio
import com.bobsdevattic.wifimapper.radar.TrackerHaptics
import com.bobsdevattic.wifimapper.radar.RadarViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Motion-tracker palette: phosphor cyan on near-black, red digital range.
private val TrackerBg = Color(0xFF02080F)
private val Cyan = Color(0xFF3FE6FF)
private val CyanDim = Color(0xFF1B6F86)
private val RangeRed = Color(0xFFFF3B2F)


private fun radiusFor(distanceM: Double, maxR: Float, outerM: Double): Float =
    (0.14f + 0.86f * TrackerMath.rangeFraction(distanceM, outerM)) * maxR   // inner 14% is the emitter

/**
 * Forward-facing "motion tracker" radar: the fan shows the 180° in front of you.
 * Pings sweep outward (faster as the signal gets stronger); the target blip sits
 * at its sweep bearing and estimated range.
 */
@Composable
fun TrackerRadar(
    ui: RadarViewModel.UiState,
    pingSound: Boolean,
    pingVibrate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Ping wave 0→1, restarted each cycle; cadence follows proximity.
    val pulse = remember { Animatable(0f) }
    val latest by rememberUpdatedState(ui)
    // Zoom: distance to the fan's outer edge. Pinch between 3 ft and ~330 ft;
    // beyond 100 ft the grid fades out (distance estimates are guesswork there).
    var outerM by rememberSaveable { mutableDoubleStateOf(TrackerMath.DEFAULT_OUTER_M) }
    // Range rings in the chosen unit, adapted to the zoom (3/10/30/100 ft by default).
    val unit = LocalDistanceUnit.current
    val rings = remember(unit, outerM) { UnitFormat.trackerRings(unit, outerM) }
    val gridAlpha = TrackerMath.gridAlpha(outerM)
    val audio = remember { TrackerAudio.create() }
    DisposableEffect(audio) { onDispose { audio?.release() } }
    val context = LocalContext.current
    val haptics = remember { TrackerHaptics.create(context) }
    LaunchedEffect(pingSound) {
        while (true) {
            pulse.snapTo(0f)
            if (pingSound && latest.rssi != null) audio?.ping()
            val period = (2200 - latest.proximity * latest.proximity * (2200 - 450)).toInt()
            pulse.animateTo(1f, tween(durationMillis = period, easing = LinearEasing))
        }
    }

    // Return bleep + buzz: fires once per sweep, the moment the wave reaches the
    // target's range. The wave's progress and the blip's radius share the same log scale.
    LaunchedEffect(pingSound, pingVibrate) {
        var prev = 0f
        snapshotFlow { pulse.value }.collect { now ->
            val range = latest.distanceRange
            if ((pingSound || pingVibrate) && range != null && latest.rssi != null) {
                val target = TrackerMath.rangeFraction(sqrt(range.first * range.second), outerM)
                if (TrackerMath.crossed(prev, now, target)) {
                    if (pingSound) audio?.blip(latest.proximity)
                    if (pingVibrate) haptics?.blip(latest.proximity)
                }
            }
            prev = now
        }
    }

    val cardinalPaint = remember { compassPaint(34f, android.graphics.Color.rgb(0x3F, 0xE6, 0xFF), bold = true) }
    val minorPaint = remember { compassPaint(22f, android.graphics.Color.rgb(0x1B, 0x9F, 0xC0), bold = false) }
    val northPaint = remember { compassPaint(40f, android.graphics.Color.rgb(0xFF, 0x3B, 0x2F), bold = true) }
    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true; textSize = 26f; color = android.graphics.Color.rgb(0x3F, 0xE6, 0xFF)
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TrackerBg)
            .border(1.dp, CyanDim, RoundedCornerShape(10.dp))
            .padding(8.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                // Two-finger pinch zooms; one-finger drags fall through so the page still scrolls.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) outerM = TrackerMath.zoomed(outerM, zoom)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { outerM = TrackerMath.DEFAULT_OUTER_M }) },
        ) {
            val cx = size.width / 2
            val cy = size.height * 0.94f
            val maxR = min(size.width / 2 * 0.96f, size.height * 0.9f)

            fun at(relDeg: Float, r: Float) = Offset(
                cx + r * sin(Math.toRadians(relDeg.toDouble())).toFloat(),
                cy - r * cos(Math.toRadians(relDeg.toDouble())).toFloat(),
            )

            // Sweep "returns": the strongest direction is highlighted; the other
            // directions are graded relative to the runner-up (see SectorShading).
            val means = ui.sectorMeans
            if (means.count { it != null } >= 2) {
                val w = 360f / means.size
                val peakIdx = ui.bearingDeg?.takeIf { ui.contrastDb >= 3f }
                    ?.let { SectorShading.sectorOf(it, means.size) }
                val shades = SectorShading.shade(means, peakIdx)
                val r0 = maxR * 0.14f
                val outer = maxR * 0.93f                            // stop inside the compass labels
                fun wedge(rel: Float) = Path().apply {
                    val s0 = at(rel - w / 2, r0); moveTo(s0.x, s0.y)
                    for (k in 0..4) { val p = at(rel - w / 2 + w * k / 4, outer); lineTo(p.x, p.y) }
                    val e = at(rel + w / 2, r0); lineTo(e.x, e.y); close()
                }
                var peakPath: Path? = null
                shades.forEachIndexed { i, sh ->
                    if (sh == null) return@forEachIndexed
                    val rel = CompassMath.relative((i + 0.5f) * w, ui.headingDeg)
                    if (abs(rel) > 90f - w / 2) return@forEachIndexed     // keep above the baseline
                    val path = wedge(rel)
                    if (sh.peak) { peakPath = path; return@forEachIndexed }
                    // Radial gradient: faint near the emitter, strongest at the rim.
                    val a = 0.03f + 0.24f * sh.level * sh.level
                    drawPath(path, Brush.radialGradient(
                        0f to Cyan.copy(alpha = a * 0.2f), 1f to Cyan.copy(alpha = a),
                        center = Offset(cx, cy), radius = outer,
                    ))
                }
                peakPath?.let { path ->
                    drawPath(path, Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.10f), 0.6f to Cyan.copy(alpha = 0.35f), 1f to Cyan.copy(alpha = 0.6f),
                        center = Offset(cx, cy), radius = outer,
                    ))
                    drawPath(path, Cyan.copy(alpha = 0.25f), style = Stroke(10f))     // glow
                    drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(2.5f))
                }
            }

            // The grid is locked to the compass: features sit at fixed bearings and
            // sweep across the fan as you turn. rel(b) = where bearing b appears.
            val heading = ui.headingDeg
            fun rel(bearing: Float) = CompassMath.relative(bearing, heading)
            val headingRad = Math.toRadians(heading.toDouble()).toFloat()

            // Range rings: glow + segmented arc whose dashes rotate with heading,
            // plus bracket ticks every 15° of bearing.
            // Zoomed out past 100 ft, the whole grid fades by gridAlpha.
            textPaint.alpha = (255 * gridAlpha).roundToInt()
            rings.forEachIndexed { idx, (d, ringLabel) ->
                val r = radiusFor(d, maxR, outerM)
                val dashes = if (idx == rings.lastIndex) null
                    else PathEffect.dashPathEffect(floatArrayOf(38f, 10f), phase = r * headingRad)
                glowArc(cx, cy, r, dashes, gridAlpha)
                for (b in 0 until 360 step 15) {
                    val a = rel(b.toFloat()); if (abs(a) > 90f) continue
                    drawLine(Cyan.copy(alpha = 0.8f * gridAlpha), at(a, r - 7f), at(a, r + 7f), strokeWidth = 2f)
                }
                val lbl = at(84f, r)                       // distance labels stay screen-fixed
                drawContext.canvas.nativeCanvas.drawText(ringLabel, lbl.x + 6f, lbl.y - 6f, textPaint)
            }
            textPaint.alpha = 255

            // Spokes every 30° of bearing, with terminal dots; compass labels outside.
            val spokeEnd = maxR * 0.93f
            for (b in 0 until 360 step 30) {
                val a = rel(b.toFloat()); if (abs(a) > 88f) continue     // not along the baseline
                val isNorth = b == 0
                val col = if (isNorth) RangeRed else Cyan
                val inner = at(a, maxR * 0.16f); val outer = at(a, spokeEnd)
                drawLine(col.copy(alpha = 0.15f * gridAlpha), inner, outer, strokeWidth = 7f)
                drawLine(col.copy(alpha = (if (isNorth) 0.95f else 0.75f) * gridAlpha), inner, outer, strokeWidth = if (isNorth) 3f else 2f)
                drawCircle(col.copy(alpha = gridAlpha), 5f, outer); drawCircle(col.copy(alpha = 0.25f * gridAlpha), 11f, outer)
            }
            CompassMath.POINTS.forEach { (b, name, cardinal) ->
                val a = rel(b); if (abs(a) > 86f) return@forEach
                val p = at(a, maxR * 0.985f)
                val paint = when {
                    name == "N" -> northPaint
                    cardinal -> cardinalPaint
                    else -> minorPaint
                }
                drawContext.canvas.nativeCanvas.drawText(name, p.x, p.y + paint.textSize / 3f, paint)
            }

            // North is behind you: show which way to turn to face it.
            val northRel = rel(0f)
            if (abs(northRel) > 86f) {
                val left = northRel < 0
                val x = if (left) cx - maxR + 8f else cx + maxR - 8f
                northPaint.textAlign = if (left) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                // Raised clear of the distance labels near the baseline.
                drawContext.canvas.nativeCanvas.drawText(if (left) "\u25C0 N" else "N \u25B6", x, cy - maxR * 0.22f, northPaint)
                northPaint.textAlign = android.graphics.Paint.Align.CENTER
            }

            // Fixed "you are facing" notch at the top of the fan.
            val notch = Path().apply {
                val t = at(0f, maxR * 0.945f)
                moveTo(t.x, t.y + 14f); lineTo(t.x - 10f, t.y - 4f); lineTo(t.x + 10f, t.y - 4f); close()
            }
            drawPath(notch, Color.White.copy(alpha = 0.9f))

            // Baseline.
            drawLine(Cyan.copy(alpha = 0.6f), Offset(cx - maxR, cy), Offset(cx + maxR, cy), strokeWidth = 2f)

            // Ping wave.
            val pr = maxR * (0.14f + 0.86f * pulse.value)
            val fade = (1f - pulse.value).coerceIn(0f, 1f)
            drawArc(
                Cyan.copy(alpha = 0.18f * fade), 180f, 180f, false,
                Offset(cx - pr, cy - pr), Size(pr * 2, pr * 2), style = Stroke(18f),
            )
            drawArc(
                Cyan.copy(alpha = 0.85f * fade), 180f, 180f, false,
                Offset(cx - pr, cy - pr), Size(pr * 2, pr * 2), style = Stroke(3f),
            )

            // Target: blip at bearing + range, or a range arc if direction unknown.
            val range = ui.distanceRange
            if (range != null && ui.rssi != null) {
                val mid = sqrt(range.first * range.second)
                val r = radiusFor(mid, maxR, outerM)
                val beyond = mid > outerM                  // further than this zoom shows: pinned to the edge
                val bearing = ui.bearingDeg?.takeIf { ui.contrastDb >= 3f }
                if (bearing == null) {
                    drawArc(
                        RangeRed.copy(alpha = 0.55f), 180f, 180f, false, Offset(cx - r, cy - r), Size(r * 2, r * 2),
                        style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    )
                } else {
                    val rel = (((bearing - ui.headingDeg) % 360f) + 540f) % 360f - 180f
                    val behind = abs(rel) > 90f
                    val p = at(rel.coerceIn(-88f, 88f), if (behind) maxR * 0.97f else r)
                    // Brighten as the ping wave passes over the blip.
                    val hit = (1f - abs(pr - r) / (maxR * 0.25f)).coerceIn(0f, 1f)
                    drawCircle(Cyan.copy(alpha = 0.18f + 0.3f * hit), 30f, p)
                    drawCircle(Cyan.copy(alpha = 0.45f + 0.4f * hit), 16f, p)
                    drawCircle(Color.White, 7f, p)
                    if (beyond && !behind) drawCircle(
                        Color.White.copy(alpha = 0.85f), 24f, p,
                        style = Stroke(2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                    if (behind) drawContext.canvas.nativeCanvas.drawText(
                        if (rel < 0) "◀ BEHIND" else "BEHIND ▶",
                        if (rel < 0) p.x + 24f else p.x - 150f, p.y + 40f, textPaint,
                    )
                }
            }

            // Emitter dome.
            val er = maxR * 0.12f
            drawArc(
                Brush.radialGradient(listOf(Color(0xFFBFF6FF), Cyan.copy(alpha = 0.55f), Color.Transparent), Offset(cx, cy), er * 1.4f),
                180f, 180f, true, Offset(cx - er, cy - er), Size(er * 2, er * 2),
            )
            drawArc(Cyan, 180f, 180f, false, Offset(cx - er, cy - er), Size(er * 2, er * 2), style = Stroke(3f))
        }

        // Readout bar.
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                ReadoutText("SIG ${ui.rssi?.let { "$it dBm" } ?: "---"}")
                ReadoutText("HDG ${"%03d".format(ui.headingDeg.roundToInt() % 360)}°")
                ReadoutText("RNG ${UnitFormat.viewRange(outerM, unit)}")
            }
            Text(
                ui.distanceRange?.let { UnitFormat.range(it, unit) } ?: UnitFormat.emptyRange(unit),
                style = TextStyle(
                    color = RangeRed, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                ),
                modifier = Modifier
                    .border(1.dp, RangeRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .background(Color(0xFF140404), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Column(horizontalAlignment = Alignment.End) {
                ReadoutText("BRG ${ui.bearingDeg?.takeIf { ui.contrastDb >= 3f }?.let {
                    "%03d° %s".format(it.roundToInt() % 360, RadarAccumulator.compassPoint(it))
                } ?: "---"}")
                ReadoutText("SWP ${(ui.coverage * 100).roundToInt()}%")
            }
        }
        Text(
            "Pinch to zoom · double-tap to reset",
            style = TextStyle(color = CyanDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
        )
    }
}

@Composable
private fun ReadoutText(text: String) = Text(
    text,
    style = TextStyle(color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
)

/** A forward-half ring with a phosphor glow (wide faint stroke under a thin bright one). */
private fun DrawScope.glowArc(cx: Float, cy: Float, r: Float, effect: PathEffect?, alpha: Float = 1f) {
    val tl = Offset(cx - r, cy - r); val sz = Size(r * 2, r * 2)
    drawArc(Cyan.copy(alpha = 0.12f * alpha), 180f, 180f, false, tl, sz, style = Stroke(14f))
    drawArc(Cyan.copy(alpha = 0.9f * alpha), 180f, 180f, false, tl, sz, style = Stroke(2.5f, cap = StrokeCap.Butt, pathEffect = effect))
}

private fun compassPaint(size: Float, color: Int, bold: Boolean) = android.graphics.Paint().apply {
    isAntiAlias = true
    textSize = size
    this.color = color
    textAlign = android.graphics.Paint.Align.CENTER
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
        if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    setShadowLayer(8f, 0f, 0f, color)                // phosphor glow
}
