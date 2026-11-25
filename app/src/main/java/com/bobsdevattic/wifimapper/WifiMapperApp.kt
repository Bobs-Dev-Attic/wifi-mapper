package com.bobsdevattic.wifimapper

import android.app.Application
import com.bobsdevattic.wifimapper.capture.PoseSource
import com.bobsdevattic.wifimapper.capture.WallSource
import com.bobsdevattic.wifimapper.data.MeasurementRepository

/**
 * Process-wide singletons. Kept deliberately small — a DI framework (Hilt) can
 * replace this later without touching call sites that use [repository].
 */
class WifiMapperApp : Application() {
    val repository: MeasurementRepository by lazy { MeasurementRepository.from(this) }
    /** Shared so the AR screen can push poses and the capture VM can read them. */
    val poseSource: PoseSource by lazy { PoseSource() }
    /** Shared so the AR screen can push detected walls to the capture VM. */
    val wallSource: WallSource by lazy { WallSource() }
}
