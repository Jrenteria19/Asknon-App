package com.example.asknonwear // Asegúrate que el package sea el correcto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.forEach
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // Para kotlinx-coroutines-play-services
import java.nio.charset.StandardCharsets

class WearActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // --- Variables para detección de agitón ---
    private var lastUpdate: Long = 0
    private var last_x: Float = 0.0f
    private var last_y: Float = 0.0f
    private var last_z: Float = 0.0f
    // AJUSTA ESTE UMBRAL SEGÚN PRUEBAS:
    // Más alto = requiere agitón más fuerte. Más bajo = más sensible.
    private val shakeThresholdGravity = 2.0F // Umbral basado en G-force
    private val shakeTimeLapse: Long = 200 // Tiempo mínimo entre detecciones
    private var mShakeTimestamp: Long = 0
    // --- Fin variables para detección de agitón ---

    // --- Estado para la UI y Diálogo ---
    private var pendingQuestionsCount by mutableStateOf(0)
    private var showConfirmDialog by mutableStateOf(false)
    // --- Fin Estado para la UI y Diálogo ---

    companion object {
        private const val TAG = "WearActivity"
        // Path para enviar el comando de aprobar todo a la app móvil
        private const val APPROVE_ALL_QUESTIONS_PATH = "/approve_all_questions"
        // Capacidad que la app móvil debe declarar
        private const val MOBILE_APP_CAPABILITY = "asknon_wear_app_capability"
    }

    // --- BroadcastReceiver para actualizar el conteo de preguntas ---
    private val questionsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == YourMessageListenerService.ACTION_PENDING_QUESTIONS_UPDATE) {
                val count = intent.getIntExtra(YourMessageListenerService.EXTRA_PENDING_QUESTIONS_COUNT, -1)
                if (count != -1) { // Usar -1 como no encontrado para diferenciar de 0
                    Log.d(TAG, "Broadcast recibido. Nuevo conteo de preguntas pendientes: $count")
                    pendingQuestionsCount = count
                } else {
                    Log.w(TAG, "Broadcast recibido pero sin conteo válido.")
                }
            }
        }
    }
    // --- Fin BroadcastReceiver ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            Log.e(TAG, "Acelerómetro no disponible en este dispositivo.")
            Toast.makeText(this, "Acelerómetro no disponible", Toast.LENGTH_LONG).show()
            // Considera deshabilitar la funcionalidad de agitón si el sensor no está
        }

        setContent {
            MaterialTheme { // Es bueno envolver en MaterialTheme
                WearAppUI(
                    questionCount = pendingQuestionsCount,
                    showDialog = showConfirmDialog,
                    onConfirmShake = {
                        sendApproveAllCommandToMobile()
                        showConfirmDialog = false
                    },
                    onDismissDialog = {
                        showConfirmDialog = false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Registrar listener del sensor
        accelerometer?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Listener del acelerómetro registrado.")
        }
        // Registrar BroadcastReceiver
        val filter = IntentFilter(YourMessageListenerService.ACTION_PENDING_QUESTIONS_UPDATE)
        LocalBroadcastManager.getInstance(this).registerReceiver(questionsUpdateReceiver, filter)
        Log.d(TAG, "BroadcastReceiver para conteo de preguntas registrado.")
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar listener del sensor
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Listener del acelerómetro desregistrado.")
        // Desregistrar BroadcastReceiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(questionsUpdateReceiver)
        Log.d(TAG, "BroadcastReceiver para conteo de preguntas desregistrado.")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No es crucial para este caso de uso simple
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // gForce aproxima la fuerza G total menos la contribución de la gravedad
            val gForce = kotlin.math.sqrt(gX * gX + gY * gY + gZ * gZ)

            if (gForce > shakeThresholdGravity) {
                val now = System.currentTimeMillis()
                // Ignora agitones demasiado cercanos en el tiempo
                if (mShakeTimestamp + shakeTimeLapse > now) {
                    return
                }
                mShakeTimestamp = now
                Log.d(TAG, "¡Agitón detectado! Fuerza G: $gForce")

                if (!showConfirmDialog) {
                    lifecycleScope.launch { // Asegura que se ejecute en el hilo principal para UI
                        showConfirmDialog = true
                    }
                }
            }
        }
    }

    private fun sendApproveAllCommandToMobile() {
        lifecycleScope.launch {
            try {
                val capabilityInfo = Wearable.getCapabilityClient(this@WearActivity)
                    .getCapability(MOBILE_APP_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                    .await() // Usando await de kotlinx-coroutines-play-services

                val connectedNodes = capabilityInfo.nodes
                if (connectedNodes.isEmpty()) {
                    Log.w(TAG, "No hay nodos móviles conectados con la capacidad: $MOBILE_APP_CAPABILITY")
                    Toast.makeText(this@WearActivity, "Móvil no conectado", Toast.LENGTH_SHORT).show()
                } else {
                    val commandData = "approve_all".toByteArray(StandardCharsets.UTF_8)
                    connectedNodes.forEach { node ->
                        Wearable.getMessageClient(this@WearActivity)
                            .sendMessage(node.id, APPROVE_ALL_QUESTIONS_PATH, commandData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Comando '$APPROVE_ALL_QUESTIONS_PATH' enviado a ${node.displayName} con éxito.")
                                Toast.makeText(this@WearActivity, "Comando enviado...", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { exception ->
                                Log.e(TAG, "Error al enviar comando '$APPROVE_ALL_QUESTIONS_PATH' a ${node.displayName}: $exception")
                                Toast.makeText(this@WearActivity, "Fallo al enviar", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al obtener nodos o enviar mensaje: ${e.localizedMessage}", e)
                Toast.makeText(this@WearActivity, "Error de comunicación", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

// --- Composable para la UI ---
@Composable
fun WearAppUI(
    questionCount: Int,
    showDialog: Boolean,
    onConfirmShake: () -> Unit,
    onDismissDialog: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Preguntas Pendientes:",
                style = MaterialTheme.typography.title3
            )
            Text(
                text = "$questionCount",
                style = MaterialTheme.typography.display1,
                fontSize = 60.sp // Hacer el número más grande
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Agita para aprobar todas",
                style = MaterialTheme.typography.caption1,
                textAlign = TextAlign.Center
            )
        }

        if (showDialog) {
            Alert(
                title = { Text("Confirmar Acción", textAlign = TextAlign.Center) },
                message = { Text("¿Aprobar todas las preguntas pendientes?", textAlign = TextAlign.Center, style = MaterialTheme.typography.body2) },
                onDismissRequest = onDismissDialog // Necesario para que el diálogo se pueda cerrar
            ) {
                // Botón de Confirmar
                item {
                    Button(
                        onClick = onConfirmShake,
                        colors = ButtonDefaults.primaryButtonColors(),
                        modifier = Modifier.fillMaxWidth(0.8f) // Ocupa el 80% del ancho
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Confirmar")
                        Text("Sí, Aprobar")
                    }
                }
                // Botón de Cancelar
                item {
                    Button(
                        onClick = onDismissDialog,
                        colors = ButtonDefaults.secondaryButtonColors(),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancelar")
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}
    