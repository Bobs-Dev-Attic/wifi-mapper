package com.bobsdevattic.wifimapper.radar

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bobsdevattic.wifimapper.model.TrackerMath

/** Short buzz when the tracker's ping reaches the blip; firmer as you get closer. */
class TrackerHaptics private constructor(private val vibrator: Vibrator) {

    fun blip(proximity: Float) {
        val ms = TrackerMath.blipVibrationMs(proximity)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (vibrator.hasAmplitudeControl()) TrackerMath.blipVibrationAmplitude(proximity)
                    else VibrationEffect.DEFAULT_AMPLITUDE
                vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(ms)
            }
        }
    }

    companion object {
        /** null if the phone has no vibrator. */
        fun create(context: Context): TrackerHaptics? {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            return v?.takeIf { it.hasVibrator() }?.let(::TrackerHaptics)
        }
    }
}
