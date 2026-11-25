package com.bobsdevattic.wifimapper.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bobsdevattic.wifimapper.model.CompassMath
import com.bobsdevattic.wifimapper.model.RadarAccumulator
import com.bobsdevattic.wifimapper.model.SignalDetails
import com.bobsdevattic.wifimapper.radar.RadarViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Motion-tracker palette: phosphor cyan on near-black, red digital range.
private val TrackerBg = Color(0xFF02080F)
private val Cyan = Color(0xFF3FE6FF)
private val CyanDim = Color(0xFF1B6F86)
private val RangeRed = Color(0xFFFF3B2F)

/** Range rings (metres) and the log scale that places them. */
private val RINGS = listOf(1.0, 3.0, 10.0, 30.0)
private const val MIN_M = 0.5
private const val MAX_M = 40.0

private fun radiusFor(distanceM: Double, maxR: Float): Float {
    val t = (log10(distanceM.coerceIn(MIN_M, MAX_M)) - log10(MIN_M)) / (log10(MAX_M) - log10(MIN_M))
    return (0.14f + 0.86f * t.toFloat()) * maxR       // inner 14% is the emitter
}

/**
 * Forward-facing "motion tracker" radar: the fan shows the 180° in front of you.
 * Pings sweep outward (faster as the signal gets stronger); the target blip sits
 * at its sweep bearing and estimated range.
 */
@Composable
fun TrackerRadar(
    ui: RadarViewModel.UiState,
    pingSound: Boolean,
    modifier: Modifier = Modifier,
) {
    // Ping wave 0→1, restarted each cycle; cadence follows proximity.
    val pulse = remember { Animatable(0f) }
    val latest by rememberUpdatedState(ui)
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull() }
    DisposableEffect(tone) { onDispose { tone?.release() } }
    LaunchedEffect(pingSound) {
        while (true) {
            pulse.snapTo(0f)
            if (pingSound && latest.rssi != null) tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 45)
            val period = (2200 - latest.proximity * latest.proximity * (2200 - 450)).toInt()
            pulse.animateTo(1f, tween(durationMillis = period, easing = LinearEasing))
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
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f)) {
            val cx = size.width / 2
            val cy = size.height * 0.94f
            val maxR = min(size.width / 2 * 0.96f, size.height * 0.9f)

            fun at(relDeg: Float, r: Float) = Offset(
                cx + r * sin(Math.toRadians(relDeg.toDouble())).toFloat(),
                cy - r * cos(Math.toRadians(relDeg.toDouble())).toFloat(),
            )

            // Faint "returns" from the sweep within the forward fan.
            val means = ui.sectorMeans
            val filled = means.filterNotNull()
            if (filled.size >= 2) {
                val lo = filled.min(); val span = (filled.max() - lo).coerceAtLeast(1f)
                val w = 360f / means.size
                means.forEachIndexed { i, m ->
                    if (m == null) return@forEachIndexed
                    val centre = (i + 0.5f) * w
                    val rel = (((centre - ui.headingDeg) % 360f) + 540f) % 360f - 180f
                    if (abs(rel) > 90f - w / 2) return@forEachIndexed     // keep above the baseline
                    val t = (m - lo) / span
                    val a = t * t * 0.16f                                   // subtle: the field stays dark
                    val path = Path().apply {
                        val r0 = maxR * 0.14f
                        moveTo(at(rel - w / 2, r0).x, at(rel - w / 2, r0).y)
                        val outer = radiusFor(RINGS.last(), maxR)          // stop at the 30 m ring
                        for (k in 0..4) { val p = at(rel - w / 2 + w * k / 4, outer); lineTo(p.x, p.y) }
                        val e = at(rel + w / 2, r0); lineTo(e.x, e.y); close()
                    }
                    drawPath(path, Cyan.copy(alpha = a))
                }
            }

            // The grid is locked to the compass: features sit at fixed bearings and
            // sweep across the fan as you turn. rel(b) = where bearing b appears.
            val heading = ui.headingDeg
            fun rel(bearing: Float) = CompassMath.relative(bearing, heading)
            val headingRad = Math.toRadians(heading.toDouble()).toFloat()

            // Range rings: glow + segmented arc whose dashes rotate with heading,
            // plus bracket ticks every 15° of bearing.
            RINGS.forEachIndexed { idx, d ->
                val r = radiusFor(d, maxR)
                val dashes = if (idx == RINGS.lastIndex) null
                    else PathEffect.dashPathEffect(floatArrayOf(38f, 10f), phase = r * headingRad)
                glowArc(cx, cy, r, dashes)
                for (b in 0 until 360 step 15) {
                    val a = rel(b.toFloat()); if (abs(a) > 90f) continue
                    drawLine(Cyan.copy(alpha = 0.8f), at(a, r - 7f), at(a, r + 7f), strokeWidth = 2f)
                }
                val lbl = at(84f, r)                       // distance labels stay screen-fixed
                drawContext.canvas.nativeCanvas.drawText("${d.toInt()}m", lbl.x + 6f, lbl.y - 6f, textPaint)
            }

            // Spokes every 30° of bearing, with terminal dots; compass labels outside.
            val spokeEnd = maxR * 0.93f
            for (b in 0 until 360 step 30) {
                val a = rel(b.toFloat()); if (abs(a) > 88f) continue     // not along the baseline
                val isNorth = b == 0
                val col = if (isNorth) RangeRed else Cyan
                val inner = at(a, maxR * 0.16f); val outer = at(a, spokeEnd)
                drawLine(col.copy(alpha = 0.15f), inner, outer, strokeWidth = 7f)
                drawLine(col.copy(alpha = if (isNorth) 0.95f else 0.75f), inner, outer, strokeWidth = if (isNorth) 3f else 2f)
                drawCircle(col, 5f, outer); drawCircle(col.copy(alpha = 0.25f), 11f, outer)
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
                val r = radiusFor(mid, maxR)
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
            }
            Text(
                ui.distanceRange?.let { SignalDetails.formatRange(it) } ?: "--- m",
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
    }
}

@Composable
private fun ReadoutText(text: String) = Text(
    text,
    style = TextStyle(color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
)

/** A forward-half ring with a phosphor glow (wide faint stroke under a thin bright one). */
private fun DrawScope.glowArc(cx: Float, cy: Float, r: Float, effect: PathEffect?) {
    val tl = Offset(cx - r, cy - r); val sz = Size(r * 2, r * 2)
    drawArc(Cyan.copy(alpha = 0.12f), 180f, 180f, false, tl, sz, style = Stroke(14f))
    drawArc(Cyan.copy(alpha = 0.9f), 180f, 180f, false, tl, sz, style = Stroke(2.5f, cap = StrokeCap.Butt, pathEffect = effect))
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
