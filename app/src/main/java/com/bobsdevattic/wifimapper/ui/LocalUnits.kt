package com.bobsdevattic.wifimapper.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.bobsdevattic.wifimapper.model.DistanceUnit

/** The user's chosen distance unit, provided once at the app root. */
val LocalDistanceUnit = staticCompositionLocalOf { DistanceUnit.METRIC }
