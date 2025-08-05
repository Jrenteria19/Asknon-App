import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var shakeTimestamp: Long = 0
    private var shakeCount = 0

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gForce = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH)
            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            if (gForce > SHAKE_THRESHOLD_GRAVITY * SHAKE_THRESHOLD_GRAVITY) {
                val now = System.currentTimeMillis()
                if (shakeTimestamp == 0L) {
                    shakeTimestamp = now
                }

                if (now - shakeTimestamp > SHAKE_SLOP_TIME_MS) {
                    onShake.invoke()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se necesita implementación
    }
}