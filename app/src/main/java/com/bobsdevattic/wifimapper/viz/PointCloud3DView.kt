package com.bobsdevattic.wifimapper.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.bobsdevattic.wifimapper.model.Vector3
import kotlin.math.cos
import kotlin.math.sin

/** A single renderable point: world position (metres), colour, floor, RSSI (dBm). */
data class CloudPoint(
    val position: Vector3,
    val color: Color,
    val floor: Int,
    val rssi: Int,
    /** False when the selected signal source wasn't heard at this point. */
    val measured: Boolean = true,
)

/**
 * Lightweight interactive 3D scatter/heatmap rendered on a Compose [Canvas].
 *
 * One-finger drag orbits (yaw/pitch); pinch zooms. Points are coloured by signal
 * strength and depth-sorted so nearer points draw on top. This intentionally uses
 * no GPU/SceneView dependency — it's the dependable default viewer. A Filament/
 * SceneView renderer can be swapped in later for large clouds or AR overlay.
 */
@Composable
fun PointCloud3DView(
    points: List<CloudPoint>,
    modifier: Modifier = Modifier,
    // When set, these points are ringed and labelled (strongest / weakest signal).
    // They must be instances from [points] so projection matches.
    strongest: CloudPoint? = null,
    weakest: CloudPoint? = null,
    // Detected walls, drawn as translucent panels labelled with signal loss.
    walls: List<WallRender> = emptyList(),
    // Estimated transmitter locations (router, mesh node, Bluetooth device).
    markers: List<MapMarker> = emptyList(),
) {
    var yaw by remember { mutableFloatStateOf(0.6f) }
    var pitch by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }

    val wallLabelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }
    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
        }
    }

    // Centre and auto-fit the cloud once per data set.
    val centroid = remember(points) {
        if (points.isEmpty()) Vector3.ZERO
        else points.fold(Vector3.ZERO) { a, p -> a + p.position } * (1f / points.size)
    }
    val extent = remember(points) {
        points.maxOfOrNull { it.position.distanceTo(centroid) }?.coerceAtLeast(0.5f) ?: 1f
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        yaw += pan.x * 0.01f
                        pitch = (pitch + pan.y * 0.01f).coerceIn(-1.5f, 1.5f)
                        scale = (scale * zoom).coerceIn(0.2f, 8f)
                    }
                }
        ) {
            if (points.isEmpty()) return@Canvas

            val cx = size.width / 2f
            val cy = size.height / 2f
            // Fit: map the cloud's extent to ~40% of the smaller screen dimension.
            val fit = (minOf(size.width, size.height) * 0.4f) / extent

            val cosY = cos(yaw); val sinY = sin(yaw)
            val cosP = cos(pitch); val sinP = sin(pitch)

            fun project(p: Vector3): Triple<Float, Float, Float> {
                // Centre, then rotate around Y (yaw) and X (pitch).
                val dx = p.x - centroid.x
                val dy = p.y - centroid.y
                val dz = p.z - centroid.z
                // Yaw about vertical axis.
                val x1 = dx * cosY + dz * sinY
                val z1 = -dx * sinY + dz * cosY
                // Pitch about horizontal axis.
                val y2 = dy * cosP - z1 * sinP
                val z2 = dy * sinP + z1 * cosP
                val s = fit * scale
                // Orthographic screen position; z2 is depth for sorting/sizing.
                return Triple(cx + x1 * s, cy - y2 * s, z2)
            }

            // Draw a faint ground grid at the baseline floor for spatial reference.
            drawReferenceGrid(centroid, extent, ::project)

            // Wall panels go under the points so measurements stay visible.
            walls.forEach { w ->
                val c = w.corners.map { project(it) }
                val path = Path().apply {
                    moveTo(c[0].first, c[0].second)
                    for (i in 1..3) lineTo(c[i].first, c[i].second)
                    close()
                }
                // Neutral grey: walls never compete with the signal palette.
                drawPath(path, Color(0x40B0BEC5))
                drawPath(
                    path, Color(0xCCECEFF1),
                    style = Stroke(
                        width = 3f,
                        // Dashed outline when we couldn't estimate this wall's loss.
                        pathEffect = if (w.hasEstimate) null
                            else PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    ),
                )
            }

            // Depth sort: far (small/negative depth first) so near points overdraw.
            val projected = points
                .map { it to project(it.position) }
                .sortedBy { it.second.third }

            projected.forEach { (pt, pr) ->
                val (sx, sy, depth) = pr
                // Size falls off with depth for a sense of perspective.
                val norm = ((depth / extent) + 1f) / 2f
                val radius = (10f + norm * 10f) * scale.coerceAtMost(2f)
                drawCircle(
                    color = pt.color.copy(alpha = 0.85f),
                    radius = radius,
                    center = Offset(sx, sy),
                )

                // Highlight + label the strongest / weakest signal points.
                val isMax = strongest != null && pt === strongest
                val isMin = weakest != null && pt === weakest
                if (isMax || isMin) {
                    // Distinguished by SHAPE, not hue, so it's colour-blind safe:
                    // MAX = solid double ring, MIN = dashed ring. White + near-black
                    // strokes keep contrast against any palette colour.
                    val c = Offset(sx, sy)
                    val dark = Color(0xFF111111)
                    if (isMax) {
                        drawCircle(Color.White, radius + 8f, c, style = Stroke(width = 5f))
                        drawCircle(dark, radius + 14f, c, style = Stroke(width = 3f))
                        drawCircle(Color.White, radius + 19f, c, style = Stroke(width = 3f))
                    } else {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        drawCircle(dark, radius + 10f, c, style = Stroke(width = 7f, pathEffect = dash))
                        drawCircle(Color.White, radius + 10f, c, style = Stroke(width = 4f, pathEffect = dash))
                    }
                    val label = (if (isMax) "▲ MAX  " else "▼ MIN  ") + "${pt.rssi} dBm"
                    drawContext.canvas.nativeCanvas.drawText(
                        label, sx + radius + 18f, sy - radius - 6f, labelPaint,
                    )
                }
            }

            // Estimated transmitter locations: a diamond with a label.
            markers.forEach { m ->
                val (mx, my, _) = project(m.position)
                val d = 22f
                val diamond = Path().apply {
                    moveTo(mx, my - d); lineTo(mx + d, my); lineTo(mx, my + d); lineTo(mx - d, my); close()
                }
                drawPath(diamond, Color(0xFF111111), style = Stroke(width = 9f))
                drawPath(diamond, Color.White, style = Stroke(width = 4f))
                drawCircle(Color.White, 5f, Offset(mx, my))
                drawContext.canvas.nativeCanvas.drawText(m.label, mx + d + 8f, my + 10f, wallLabelPaint)
            }

            // Wall loss labels on top of everything.
            walls.forEach { w ->
                val (wx, wy, _) = project(w.center)
                val text = w.label
                val half = wallLabelPaint.measureText(text) / 2f
                drawContext.canvas.nativeCanvas.drawText(text, wx - half, wy, wallLabelPaint)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReferenceGrid(
    centroid: Vector3,
    extent: Float,
    project: (Vector3) -> Triple<Float, Float, Float>,
) {
    val half = extent
    val step = (extent / 4f).coerceAtLeast(0.5f)
    val gridColor = Color(0x33FFFFFF)
    var g = -half
    while (g <= half) {
        val a = project(Vector3(centroid.x + g, centroid.y, centroid.z - half))
        val b = project(Vector3(centroid.x + g, centroid.y, centroid.z + half))
        drawLine(gridColor, Offset(a.first, a.second), Offset(b.first, b.second), 1f)
        val c = project(Vector3(centroid.x - half, centroid.y, centroid.z + g))
        val d = project(Vector3(centroid.x + half, centroid.y, centroid.z + g))
        drawLine(gridColor, Offset(c.first, c.second), Offset(d.first, d.second), 1f)
        g += step
    }
}
