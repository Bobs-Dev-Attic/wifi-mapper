package com.bobsdevattic.wifimapper.capture

import com.bobsdevattic.wifimapper.model.WallObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Latest set of wall-like vertical surfaces ARCore currently knows about. The AR
 * surface replaces the whole set on each scan (ARCore merges and grows planes
 * over time, so the newest snapshot is the best one).
 */
class WallSource {
    private val _walls = MutableStateFlow<List<WallObservation>>(emptyList())
    val walls: StateFlow<List<WallObservation>> = _walls

    fun update(walls: List<WallObservation>) { _walls.value = walls }
    fun clear() { _walls.value = emptyList() }
}
