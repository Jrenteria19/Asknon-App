package com.example.asknon

import ShakeDetector
import android.graphics.Bitmap
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.util.*
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import android.widget.Toast
import com.google.android.gms.wearable.NodeClient
import com.google.firebase.firestore.WriteBatch
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

data class Pregunta(
    val id: String = "",
    val texto: String = "",
    val respuesta: String? = null,
    val estado: String = "pendiente", // "pendiente", "aprobada", "rechazada"
    val claseId: String = "",
    val estudianteId: String = "",
    val fechaCreacion: Date = Date()
)

class TeacherClassActivity : AppCompatActivity(), MessageClient.OnMessageReceivedListener {
    // Elementos de UI
    private lateinit var tvClassCode: TextView
    private lateinit var imgQr: ImageView
    private lateinit var rvPending: RecyclerView
    private lateinit var rvApproved: RecyclerView
    private lateinit var btnProject: Button
    private lateinit var btnDeleteClass: Button

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var classListener: ListenerRegistration? = null
    private var questionsListener: ListenerRegistration? = null

    // Datos
    private var currentClassId: String = ""
    private val pendingQuestions = mutableListOf<Pregunta>()
    private val approvedQuestions = mutableListOf<Pregunta>()

    // Adaptadores
    private lateinit var pendingAdapter: QuestionActionAdapter
    private lateinit var approvedAdapter: PreguntaAdapter

    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(this) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(this) }
    private val capabilityClient: CapabilityClient by lazy { Wearable.getCapabilityClient(this) }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector

    companion object {
        private const val TAG = "TeacherClassActivity"
        private const val PENDING_QUESTIONS_PATH = "/pending_questions_count"
        private const val APPROVE_ALL_QUESTIONS_PATH = "/approve_all_questions"
        private const val WEAR_APP_CAPABILITY = "asknon_wear_app_capability"
    }

    override fun onResume() {
        super.onResume()
        messageClient.addListener(this)
        Log.d(TAG, "MessageListener registrado")

        // --- Nuevo código para el detector de agitación ---
        accelerometer?.let {
            sensorManager.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // --- Fin del nuevo código ---
    }

    override fun onPause() {
        super.onPause()
        messageClient.removeListener(this)
        Log.d(TAG, "MessageListener desregistrado")

        // --- Nuevo código para el detector de agitación ---
        sensorManager.unregisterListener(shakeDetector)
        // --- Fin del nuevo código ---
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Mensaje recibido: ${messageEvent.path}")
        when (messageEvent.path) {
            APPROVE_ALL_QUESTIONS_PATH -> {
                runOnUiThread {
                    approveAllPendingQuestionsInFirestore()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_teacher)

        initViews()
        setupAdapters()
        checkExistingClass()

        // --- Nuevo código para el detector de agitación ---
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        shakeDetector = ShakeDetector {
            // Acción al agitar el teléfono
            Log.d(TAG, "Teléfono agitado! Aprobando preguntas.")
            approveAllPendingQuestionsInFirestore()
        }
        // --- Fin del nuevo código ---
    }

    private fun initViews() {
        tvClassCode = findViewById(R.id.tv_class_code)
        imgQr = findViewById(R.id.img_qr)
        rvPending = findViewById(R.id.rv_pending_questions)
        rvApproved = findViewById(R.id.rv_approved_questions)
        btnProject = findViewById(R.id.btn_project_tv)
        btnDeleteClass = findViewById(R.id.btn_delete_class)

        btnProject.setOnClickListener { projectFirstQuestion() }
        btnDeleteClass.setOnClickListener { showDeleteClassConfirmation() }
    }

    private fun setupAdapters() {
        pendingAdapter = QuestionActionAdapter(
            items = pendingQuestions,
            onApprove = { question -> approveQuestion(question) },
            onAnswer = { question -> showAnswerDialog(question) },
            onReject = { question -> rejectQuestion(question) }
        )

        approvedAdapter = PreguntaAdapter(approvedQuestions) { question ->
            deleteAnsweredQuestion(question)
        }

        rvPending.apply {
            layoutManager = LinearLayoutManager(this@TeacherClassActivity)
            adapter = pendingAdapter
        }

        rvApproved.apply {
            layoutManager = LinearLayoutManager(this@TeacherClassActivity)
            adapter = approvedAdapter
        }
    }

    private fun approveAllPendingQuestionsInFirestore() {
        if (currentClassId.isEmpty()) {
            Toast.makeText(this, "ID de clase no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .whereEqualTo("estado", "pendiente")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No hay preguntas pendientes", Toast.LENGTH_SHORT).show()
                    sendPendingQuestionsCountToWear(0)
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "estado", "aprobada")
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Todas las preguntas aprobadas", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al aprobar preguntas", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Error en batch", e)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener preguntas", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error al obtener preguntas", e)
            }
    }

    private fun checkExistingClass() {
        val userId = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        db.collection("clases")
            .whereEqualTo("profesorId", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    createNewClass(userId)
                } else {
                    val clase = snapshot.documents[0]
                    currentClassId = clase.id
                    setupClassInfo(clase.getString("codigo") ?: "", clase.getString("qrCode") ?: "")
                    setupQuestionsListener()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al verificar clases", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun createNewClass(profesorId: String) {
        val classId = UUID.randomUUID().toString()
        val classCode = generateUniqueClassCode()
        val qrContent = "ASK_NON_CLASS_$classId"

        val nuevaClase = hashMapOf(
            "codigo" to classCode,
            "qrCode" to qrContent,
            "profesorId" to profesorId,
            "fechaCreacion" to Calendar.getInstance().time
        )

        db.collection("clases").document(classId)
            .set(nuevaClase)
            .addOnSuccessListener {
                currentClassId = classId
                setupClassInfo(classCode, qrContent)
                setupQuestionsListener()
                Toast.makeText(this, "Clase creada exitosamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al crear clase", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupClassInfo(classCode: String, qrContent: String) {
        tvClassCode.text = "Código: $classCode"
        generateQrImage(qrContent)
    }

    private fun generateQrImage(content: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            imgQr.setImageBitmap(bmp)
        } catch (e: WriterException) {
            Toast.makeText(this, "Error al generar QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupQuestionsListener() {
        if (currentClassId.isEmpty()) return

        questionsListener = db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error al cargar preguntas", error)
                    return@addSnapshotListener
                }

                val newPendingQuestions = mutableListOf<Pregunta>()
                val newApprovedQuestions = mutableListOf<Pregunta>()

                snapshot?.documents?.forEach { doc ->
                    val pregunta = doc.toObject(Pregunta::class.java)?.copy(id = doc.id)
                    pregunta?.let {
                        when (it.estado) {
                            "pendiente" -> newPendingQuestions.add(it)
                            "aprobada" -> newApprovedQuestions.add(it)
                        }
                    }
                }

                pendingAdapter.updateData(newPendingQuestions)
                approvedAdapter.updateData(newApprovedQuestions)
                sendPendingQuestionsCountToWear(newPendingQuestions.size)
            }
    }

    private fun sendPendingQuestionsCountToWear(count: Int) {
        capabilityClient.getCapability("asknon_wear_app_capability", CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                val nodes = capabilityInfo.nodes
                if (nodes.isNotEmpty()) {
                    val messageData = count.toString().toByteArray(StandardCharsets.UTF_8)

                    nodes.forEach { node ->
                        messageClient.sendMessage(
                            node.id,
                            PENDING_QUESTIONS_PATH,
                            messageData
                        ).addOnSuccessListener {
                            Log.d(TAG, "Conteo enviado a ${node.displayName}")
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "Error al enviar a ${node.displayName}", e)
                        }
                    }
                } else {
                    Log.w(TAG, "No se encontró un nodo con la capacidad de Wear OS")
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error al obtener capacidades de Wear OS", e)
            }
    }

    private fun generateUniqueClassCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun approveQuestion(pregunta: Pregunta) {
        db.collection("preguntas").document(pregunta.id)
            .update("estado", "aprobada")
            .addOnSuccessListener {
                Toast.makeText(this, "Pregunta aprobada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al aprobar pregunta", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectQuestion(pregunta: Pregunta) {
        db.collection("preguntas").document(pregunta.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Pregunta rechazada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al rechazar pregunta", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAnswerDialog(pregunta: Pregunta) {
        val input = EditText(this).apply {
            hint = "Escribe tu respuesta"
        }

        AlertDialog.Builder(this)
            .setTitle("Responder pregunta")
            .setMessage(pregunta.texto)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val respuesta = input.text.toString().trim()
                if (respuesta.isNotEmpty()) {
                    answerQuestion(pregunta, respuesta)
                } else {
                    Toast.makeText(this, "La respuesta no puede estar vacía", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun answerQuestion(pregunta: Pregunta, respuesta: String) {
        db.collection("preguntas").document(pregunta.id)
            .update(mapOf(
                "estado" to "aprobada",
                "respuesta" to respuesta
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Respuesta guardada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al guardar respuesta", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteAnsweredQuestion(pregunta: Pregunta) {
        db.collection("preguntas").document(pregunta.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Pregunta eliminada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al eliminar pregunta", Toast.LENGTH_SHORT).show()
            }
    }

    private fun projectFirstQuestion() {
        if (approvedQuestions.isNotEmpty()) {
            if (currentClassId.isEmpty()) {
                Toast.makeText(this, "ID de clase no disponible", Toast.LENGTH_SHORT).show()
                return
            }

            Toast.makeText(
                this,
                "Pregunta aprobada. Asegúrate de que la TV esté conectada al código: $currentClassId",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "No hay preguntas aprobadas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteClassConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar clase")
            .setMessage("¿Estás seguro de que quieres eliminar esta clase? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteClass()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteClass() {
        if (currentClassId.isEmpty()) {
            Toast.makeText(this, "No hay clase para eliminar", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .get()
            .addOnSuccessListener { questionsSnapshot ->
                val batch = db.batch()
                questionsSnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }

                batch.commit()
                    .addOnSuccessListener {
                        db.collection("clases").document(currentClassId)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Clase eliminada exitosamente", Toast.LENGTH_SHORT).show()
                                logoutAndGoToMain()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error al eliminar la clase: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al eliminar las preguntas: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener preguntas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logoutAndGoToMain() {
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        classListener?.remove()
        questionsListener?.remove()
    }
}