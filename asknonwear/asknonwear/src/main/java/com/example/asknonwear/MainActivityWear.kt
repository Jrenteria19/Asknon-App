package com.example.asknonwear

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets

class MainActivityWear : ComponentActivity(),
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    // Views
    private lateinit var tvPendingCount: TextView
    private lateinit var btnApproveAll: Button

    // Wear OS
    private var pendingQuestionsCount = 0
    private lateinit var capabilityClient: CapabilityClient
    private lateinit var messageClient: MessageClient
    private lateinit var nodeClient: NodeClient
    private var connectedNodeId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val connectionCheckInterval = 3000L

    companion object {
        private const val TAG = "MainActivityWear"
        private const val PENDING_QUESTIONS_PATH = "/pending_questions_count"
        private const val APPROVE_ALL_QUESTIONS_PATH = "/approve_all_questions"
        private const val MOBILE_CAPABILITY = "asknon_mobile_app_capability"
    }

    private val connectionChecker = object : Runnable {
        override fun run() {
            verifyConnection()
            handler.postDelayed(this, connectionCheckInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar APIs de Wearable
        capabilityClient = Wearable.getCapabilityClient(this)
        messageClient = Wearable.getMessageClient(this)
        nodeClient = Wearable.getNodeClient(this)

        // Verificar Google Play Services
        checkPlayServices()

        initViews()
        updateUI(0)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val count = intent?.getIntExtra("pending_count", 0) ?: 0
        updateUI(count)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Registrando listeners")

        // Registrar listeners
        messageClient.addListener(this)
        capabilityClient.addListener(this, MOBILE_CAPABILITY)

        // Iniciar verificación de conexión
        handler.post(connectionChecker)
        verifyConnection()
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Removiendo listeners")

        // Detener verificaciones y remover listeners
        handler.removeCallbacks(connectionChecker)
        messageClient.removeListener(this)
        capabilityClient.removeListener(this)
    }

    private fun checkPlayServices() {
        GoogleApiAvailability.getInstance().apply {
            val result = isGooglePlayServicesAvailable(this@MainActivityWear)
            if (result != ConnectionResult.SUCCESS) {
                Log.e(TAG, "Play Services no disponibles: ${getErrorString(result)}")
                showToast("Actualiza Google Play Services", Toast.LENGTH_LONG)
            }
        }
    }

    private fun initViews() {
        tvPendingCount = findViewById(R.id.tv_pending_count)
        btnApproveAll = findViewById(R.id.btn_approve_all)

        btnApproveAll.setOnClickListener {
            if (pendingQuestionsCount > 0) {
                approveAllQuestions()
            } else {
                showToast("No hay preguntas pendientes")
            }
        }
    }

    private fun verifyConnection() {
        // Verificar nodos conectados directamente
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isNotEmpty()) {
                val node = nodes.first()
                if (connectedNodeId != node.id) {
                    connectedNodeId = node.id
                    Log.d(TAG, "Conectado a: ${node.displayName} (${node.id})")
                    showToast("Conectado al móvil")
                    requestCurrentCount()
                }
            } else {
                Log.w(TAG, "No hay nodos conectados")
                connectedNodeId = null
                checkCapabilities()
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Error al verificar nodos", e)
            connectedNodeId = null
        }
    }

    private fun checkCapabilities() {
        capabilityClient.getCapability(MOBILE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                if (capabilityInfo.nodes.isNotEmpty()) {
                    Log.d(TAG, "Nodo disponible pero no conectado: ${capabilityInfo.nodes}")
                    showToast("Conectando...")
                } else {
                    Log.w(TAG, "No se encontró el móvil")
                    showToast("Móvil no disponible")
                }
            }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        Log.d(TAG, "Capacidad cambiada: ${capabilityInfo.nodes}")
        verifyConnection()
    }

    private fun requestCurrentCount() {
        connectedNodeId?.let { nodeId ->
            Log.d(TAG, "Solicitando conteo actual a $nodeId")
            messageClient.sendMessage(
                nodeId,
                PENDING_QUESTIONS_PATH,
                "get_current_count".toByteArray(StandardCharsets.UTF_8)
            ).addOnSuccessListener {
                Log.d(TAG, "Solicitud de conteo enviada")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al solicitar conteo", e)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Mensaje recibido: ${messageEvent.path}")

        when (messageEvent.path) {
            PENDING_QUESTIONS_PATH -> {
                val count = String(messageEvent.data, StandardCharsets.UTF_8).toIntOrNull() ?: 0
                Log.d(TAG, "Nuevo conteo: $count")
                updateUI(count)
            }
        }
    }

    private fun updateUI(count: Int) {
        pendingQuestionsCount = count
        runOnUiThread {
            tvPendingCount.text = "Pendientes: $count"
            btnApproveAll.isEnabled = count > 0
            btnApproveAll.setBackgroundColor(
                if (count > 0) ContextCompat.getColor(this, R.color.colorPrimary)
                else ContextCompat.getColor(this, R.color.colorDisabled)
            )

            if (count > 0) {
                showToast("$count preguntas pendientes")
            }
        }
    }

    private fun approveAllQuestions() {
        connectedNodeId?.let { nodeId ->
            Log.d(TAG, "Enviando aprobación a $nodeId")
            messageClient.sendMessage(
                nodeId,
                APPROVE_ALL_QUESTIONS_PATH,
                "approve_all".toByteArray(StandardCharsets.UTF_8)
            ).addOnSuccessListener {
                Log.d(TAG, "Aprobación enviada")
                showToast("Preguntas aprobadas")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al aprobar", e)
                showToast("Error al aprobar")
            }
        } ?: run {
            showToast("No hay conexión")
            verifyConnection()
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        runOnUiThread {
            Toast.makeText(this, message, duration).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(connectionChecker)
    }
}