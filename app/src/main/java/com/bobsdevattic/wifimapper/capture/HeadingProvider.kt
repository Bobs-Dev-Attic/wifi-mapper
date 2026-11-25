package com.bobsdevattic.wifimapper.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bobsdevattic.wifimapper.model.CompassMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Compass heading (degrees, 0 = magnetic north, clockwise) of the direction the
 * BACK of the phone points while it's held UPRIGHT in front of you — i.e. the
 * way you're facing. Uses the fused rotation-vector sensor.
 *
 * Indoors, steel and electronics can skew the magnetometer by 10–20°. That's OK
 * for the radar: a consistent offset rotates every sector equally, so the
 * *relative* "turn left / right" guidance stays right.
 */
class HeadingProvider(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    val isAvailable: Boolean get() = sensor != null

    fun headings(): Flow<Float> = callbackFlow {
        val s = sensor ?: run { close(); return@callbackFlow }
        val rot = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        // Raw compass jitters a few degrees; smooth it (wrap-safe) so heading-locked
        // graphics like the tracker grid don't shimmer.
        val smoother = CompassMath.HeadingSmoother(alpha = 0.25f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, event.values)
                // Phone upright: use the camera axis (device −Z) as "forward".
                SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                trySend(smoother.add((deg + 360f) % 360f))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
