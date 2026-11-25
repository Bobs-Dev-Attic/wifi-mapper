package com.bobsdevattic.wifimapper.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.bobsdevattic.wifimapper.model.Vector3
import com.bobsdevattic.wifimapper.model.WallObservation
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView

/**
 * ARCore camera surface rendered via SceneView. On each frame it extracts the
 * device pose and pushes it into [poseSource], which the FusionEngine reads.
 *
 * ──────────────────────────────────────────────────────────────────────────
 *  VERSION NOTE: SceneView's Compose API evolves between minor releases. If this
 *  file fails to compile after a SceneView bump, this is the expected place to
 *  adjust — the `ARScene(...)` parameter list and `onSessionUpdated` signature
 *  are the usual culprits. The rest of the app does not depend on SceneView.
 * ──────────────────────────────────────────────────────────────────────────
 *
 * On a device without ARCore support the host screen should not compose this;
 * capture still works with GPS + barometer + a manual "drop point" button.
 */
@Composable
fun ArCaptureSurface(
    poseSource: PoseSource,
    wallSource: WallSource,
    modifier: Modifier = Modifier,
) {
    val lastWallScanMs = remember { longArrayOf(0L) }

    val engine = rememberEngine()
    val view = rememberView(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()

    ARScene(
        modifier = modifier,
        engine = engine,
        view = view,
        childNodes = childNodes,
        cameraNode = cameraNode,
        planeRenderer = true,
        sessionConfiguration = { session, config ->
            // Walls are vertical planes; also keep floors for the plane renderer.
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            // Depth (supported on the S25 Edge) improves tracking/occlusion on
            // low-texture surfaces. Optional: skipped where unsupported.
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
        },
        onSessionUpdated = { session, frame ->
            val camera = frame.camera
            val tracking = camera.trackingState == TrackingState.TRACKING
            val t = camera.pose.translation // [x, y, z] in the AR-local frame
            poseSource.update(Vector3(t[0], t[1], t[2]), tracking)

            // Re-scan planes at most twice a second (cheap, but no need per frame).
            val now = System.currentTimeMillis()
            if (now - lastWallScanMs[0] >= 500L) {
                lastWallScanMs[0] = now
                wallSource.update(extractWalls(session))
            }
        },
    )
}

/** Minimum size (m) for a vertical plane to count as wall-like. */
private const val MIN_WALL_WIDTH_M = 0.6f
private const val MIN_WALL_HEIGHT_M = 0.5f

/**
 * Converts ARCore's current vertical planes into [WallObservation]s. Planes that
 * have been merged into another (subsumed) or stopped tracking are skipped.
 */
private fun extractWalls(session: Session): List<WallObservation> =
    session.getAllTrackables(Plane::class.java).mapNotNull { plane ->
        if (plane.type != Plane.Type.VERTICAL) return@mapNotNull null
        if (plane.trackingState == TrackingState.STOPPED) return@mapNotNull null
        if (plane.subsumedBy != null) return@mapNotNull null

        val pose = plane.centerPose
        val normal = pose.getYAxis()   // a plane's local +Y is its normal
        val xAxis = pose.getXAxis()
        // For a vertical plane, one of local X / Z is horizontal and the other
        // vertical; work out which so width/height come out right.
        val xIsHorizontal = kotlin.math.abs(xAxis[1]) < 0.5f
        val width = if (xIsHorizontal) plane.extentX else plane.extentZ
        val height = if (xIsHorizontal) plane.extentZ else plane.extentX
        if (width < MIN_WALL_WIDTH_M || height < MIN_WALL_HEIGHT_M) return@mapNotNull null

        WallObservation.of(
            center = Vector3(pose.tx(), pose.ty(), pose.tz()),
            normalX = normal[0],
            normalZ = normal[2],
            width = width,
            height = height,
        )
    }
