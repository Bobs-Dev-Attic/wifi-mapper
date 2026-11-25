package com.bobsdevattic.wifimapper.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bobsdevattic.wifimapper.model.Vector3
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
    modifier: Modifier = Modifier,
) {
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
        onSessionUpdated = { _, frame ->
            val camera = frame.camera
            val tracking = camera.trackingState == TrackingState.TRACKING
            val t = camera.pose.translation // [x, y, z] in the AR-local frame
            poseSource.update(Vector3(t[0], t[1], t[2]), tracking)
        },
    )
}
