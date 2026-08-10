package com.waypoint.gohome.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Reports the device's compass heading (degrees, 0 = magnetic north) via [onHeadingChanged]. */
class CompassSensor(context: Context, private val onHeadingChanged: (Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        onHeadingChanged((azimuthDegrees + 360) % 360)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
