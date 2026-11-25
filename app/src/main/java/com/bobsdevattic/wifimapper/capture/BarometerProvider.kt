package com.bobsdevattic.wifimapper.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Barometric pressure -> relative altitude, used as an independent vertical
 * signal to detect and label floor changes (ARCore's Y can drift over a long
 * walk; pressure does not accumulate the same drift).
 *
 * The Galaxy S25 Edge has a pressure sensor, but we treat it as optional:
 * [hasBarometer] is false on devices without one and the pipeline falls back to
 * ARCore's Y for floor inference.
 */
class BarometerProvider(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasBarometer: Boolean get() = pressureSensor != null

    /** Emits pressure in hPa (millibar). */
    fun pressureHpa(): Flow<Float> = callbackFlow {
        val sensor = pressureSensor ?: run { close(); return@callbackFlow }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }
            override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    companion object {
        /**
         * Altitude (m) of [pressureHpa] relative to [baselineHpa], via the
         * barometric formula. Positive = higher than baseline.
         */
        fun relativeAltitude(pressureHpa: Float, baselineHpa: Float): Float {
            // h = 44330 * (1 - (p/p0)^(1/5.255))
            val ratio = (pressureHpa / baselineHpa).toDouble()
            return (44330.0 * (1.0 - Math.pow(ratio, 1.0 / 5.255))).toFloat()
        }

        /** Floor index from relative altitude, assuming ~3 m storey height. */
        fun floorFromAltitude(relAltitudeM: Float, storeyHeightM: Float = 3.0f): Int =
            Math.round(relAltitudeM / storeyHeightM)
    }
}
