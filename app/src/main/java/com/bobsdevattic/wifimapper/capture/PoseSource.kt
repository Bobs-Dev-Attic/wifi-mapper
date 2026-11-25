package com.bobsdevattic.wifimapper.capture

import com.bobsdevattic.wifimapper.model.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the latest device pose from ARCore. The AR capture screen pushes updates
 * in via [update]; the FusionEngine reads [state]. Keeping this as a plain holder
 * means the fusion/capture logic has no compile-time dependency on SceneView,
 * which makes it unit-testable and robust to SceneView API churn.
 */
class PoseSource {

    data class PoseState(
        val position: Vector3 = Vector3.ZERO,
        /** True while ARCore reports TrackingState.TRACKING. */
        val tracking: Boolean = false,
        val updatedAt: Long = 0L,
    )

    private val _state = MutableStateFlow(PoseState())
    val state: StateFlow<PoseState> = _state

    fun update(position: Vector3, tracking: Boolean) {
        _state.value = PoseState(position, tracking, System.currentTimeMillis())
    }

    fun current(): PoseState = _state.value
}
