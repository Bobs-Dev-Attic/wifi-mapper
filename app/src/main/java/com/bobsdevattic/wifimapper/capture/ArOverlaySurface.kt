package com.bobsdevattic.wifimapper.capture

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bobsdevattic.wifimapper.model.Vector3
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView

/** What the overlay needs from each AR frame. */
class ArFrame(
    val view: FloatArray,         // 4x4 column-major
    val projection: FloatArray,   // 4x4 column-major
    val cameraPosition: Vector3,
    val tracking: Boolean,
)

/**
 * ARCore camera feed (via SceneView) that reports the camera's view/projection
 * matrices every frame, so a 2D overlay can draw world-anchored signal orbs.
 * Same ARScene usage as [ArCaptureSurface].
 */
@Composable
fun ArOverlaySurface(
    onFrame: (ArFrame) -> Unit,
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
        planeRenderer = false,          // keep the view clean for the overlay
        sessionConfiguration = { session, config ->
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
        },
        onSessionUpdated = { _, frame ->
            val camera = frame.camera
            val v = FloatArray(16)
            val p = FloatArray(16)
            camera.getViewMatrix(v, 0)
            camera.getProjectionMatrix(p, 0, 0.05f, 100f)
            val t = camera.pose.translation
            onFrame(ArFrame(v, p, Vector3(t[0], t[1], t[2]), camera.trackingState == TrackingState.TRACKING))
        },
    )
}
