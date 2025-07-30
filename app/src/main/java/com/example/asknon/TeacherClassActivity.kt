package com.example.asknon

import android.graphics.Bitmap
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.geometry.isEmpty
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
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import android.widget.Toast
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.firebase.firestore.WriteBatch

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

    // Datos
    private val pendingQuestions = mutableListOf<Pregunta>()
    private val approvedQuestions = mutableListOf<Pregunta>()

    // Adaptadores
    private lateinit var pendingAdapter: QuestionActionAdapter
    private lateinit var approvedAdapter: PreguntaAdapter

    companion object {
        private const val TAG_MOBILE = "TeacherClassActivity" // Para logs
        private const val PENDING_QUESTIONS_PATH = "/pending_questions_count"
        // Esta es una "capacidad" que declararás en el AndroidManifest.xml de tu app Wear OS
        // y en el archivo wear.xml de tu app móvil.
        private const val APPROVE_ALL_QUESTIONS_PATH = "/approve_all_questions"
        private const val WEAR_APP_CAPABILITY = "asknon_wear_app_capability"
        // Constante para identificar si estamos en modo debug
        private const val IS_DEBUG_MODE = true // Cambia a false para producción
    }

    override fun onResume() {
        super.onResume()
        // Registrar listener para mensajes de Wear OS
        Wearable.getMessageClient(this).addListener(this)
        Log.d(TAG_MOBILE, "MessageListener para Wear OS registrado.")
    }

    override fun onPause() {
        super.onPause()
        // Desregistrar listener
        Wearable.getMessageClient(this).removeListener(this)
        Log.d(TAG_MOBILE, "MessageListener para Wear OS desregistrado.")
    }

    // Implementación de MessageClient.OnMessageReceivedListener
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG_MOBILE, "Mensaje recibido desde Wear OS: ${messageEvent.path}")
        when (messageEvent.path) {
            APPROVE_ALL_QUESTIONS_PATH -> {
                val messageData = String(messageEvent.data) // Opcional, si envías datos
                Log.d(TAG_MOBILE, "Recibido comando para aprobar todas las preguntas. Datos: $messageData")
                approveAllPendingQuestionsInFirestore()
            }
            else -> {
                Log.w(TAG_MOBILE, "Path desconocido desde Wear OS: ${messageEvent.path}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_teacher)
        initViews()
        setupAdapters()
        checkExistingClass()
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
            Toast.makeText(this, "ID de clase no disponible.", Toast.LENGTH_SHORT).show()
            Log.w(TAG_MOBILE, "approveAll: currentClassId está vacío.")
            return
        }
        Log.d(TAG_MOBILE, "Intentando aprobar todas las preguntas pendientes para la clase: $currentClassId")
        db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .whereEqualTo("estado", "pendiente") // Solo seleccionar las pendientes
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No hay preguntas pendientes para aprobar.", Toast.LENGTH_SHORT).show()
                    Log.d(TAG_MOBILE, "No hay preguntas pendientes en Firestore para la clase $currentClassId.")
                    // Aún así, es bueno enviar el conteo 0 al reloj,
                    // aunque setupQuestionsListener debería manejarlo.
                    sendPendingQuestionsCountToWear(0)
                    return@addOnSuccessListener
                }
                val batch: WriteBatch = db.batch()
                snapshot.documents.forEach { doc ->
                    Log.d(TAG_MOBILE, "Aprobando pregunta ID: ${doc.id}")
                    batch.update(doc.reference, "estado", "aprobada")
                }
                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG_MOBILE, "Todas las preguntas pendientes (${snapshot.size()}) han sido aprobadas en Firestore.")
                        Toast.makeText(this, "Todas las preguntas pendientes aprobadas (desde reloj).", Toast.LENGTH_LONG).show()
                        // Tu setupQuestionsListener existente debería detectar este cambio,
                        // actualizar la UI del móvil, y llamar a sendPendingQuestionsCountToWear()
                        // enviando '0' al reloj automáticamente.
                        // Si quieres forzar un envío inmediato por si acaso:
                        // sendPendingQuestionsCountToWear(0)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG_MOBILE, "Error al ejecutar batch para aprobar todas las preguntas", e)
                        Toast.makeText(this, "Error al aprobar todas las preguntas.", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_MOBILE, "Error al obtener preguntas pendientes para aprobar todo", e)
                Toast.makeText(this, "Error al obtener preguntas pendientes.", Toast.LENGTH_SHORT).show()
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
                    setupQuestionsListener() // Mover aquí para asegurar que currentClassId está seteado
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
                setupQuestionsListener() // Mover aquí para asegurar que currentClassId está seteado
                Toast.makeText(this, "Clase creada exitosamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al crear clase", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupClassInfo(classCode: String, qrContent: String) {
        tvClassCode.text = "Código: $classCode\nID: $currentClassId" // Mostrar también el ID
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
        if (currentClassId.isEmpty()) return // No escuchar si no hay clase
        questionsListener = db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG_MOBILE, "Error al cargar preguntas", error)
                    Toast.makeText(this, "Error al cargar preguntas", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val newPendingQuestions = mutableListOf<Pregunta>()
                val newApprovedQuestions = mutableListOf<Pregunta>()
                snapshot?.documents?.forEach { doc ->
                    val preguntaData = doc.toObject(Pregunta::class.java)
                    if (preguntaData != null) {
                        val preguntaConIdCorrecto = preguntaData.copy(id = doc.id)
                        when (preguntaConIdCorrecto.estado) {
                            "pendiente" -> newPendingQuestions.add(preguntaConIdCorrecto)
                            "aprobada" -> newApprovedQuestions.add(preguntaConIdCorrecto)
                        }
                    }
                }
                // Actualizar los adaptadores con los nuevos datos
                pendingAdapter.updateData(newPendingQuestions)
                approvedAdapter.updateData(newApprovedQuestions)
                // <<--- AQUÍ ES DONDE ENVIAREMOS LOS DATOS A WEAR OS --- >>
                sendPendingQuestionsCountToWear(newPendingQuestions.size)
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

    private fun sendPendingQuestionsCountToWear(count: Int) {
        val messageData = count.toString().toByteArray(StandardCharsets.UTF_8)
        // Usar CapabilityClient para encontrar nodos que tengan tu app Wear OS instalada
        val capabilityInfoTask = com.google.android.gms.wearable.Wearable.getCapabilityClient(this)
            .getCapability(WEAR_APP_CAPABILITY, com.google.android.gms.wearable.CapabilityClient.FILTER_REACHABLE)
        capabilityInfoTask.addOnSuccessListener { capabilityInfo ->
            val connectedNodes = capabilityInfo.nodes
            if (connectedNodes.isEmpty()) {
                Log.d(TAG_MOBILE, "No hay dispositivos Wear OS conectados/alcanzables con la capacidad.")
                // Opcional: Toast.makeText(this, "Wear OS no conectado", Toast.LENGTH_SHORT).show()
            } else {
                connectedNodes.forEach { node ->
                    sendMessageToNode(node.id, PENDING_QUESTIONS_PATH, messageData)
                }
            }
        }
        capabilityInfoTask.addOnFailureListener { exception ->
            Log.e(TAG_MOBILE, "Error al obtener nodos con capacidad: $exception")
        }
    }

    private fun sendMessageToNode(nodeId: String, path: String, data: ByteArray) {
        com.google.android.gms.wearable.Wearable.getMessageClient(this).sendMessage(nodeId, path, data)
            .addOnSuccessListener {
                Log.d(TAG_MOBILE, "Mensaje enviado a $nodeId con éxito. Path: $path")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG_MOBILE, "Error al enviar mensaje a $nodeId. Path: $path. Error: $exception")
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
            // Verificar que currentClassId no esté vacío
            if (currentClassId.isEmpty()) {
                Toast.makeText(this, "ID de clase no disponible", Toast.LENGTH_SHORT).show()
                return
            }

            // En lugar de intentar lanzar la app de TV, simplemente muestra un mensaje
            // indicando que la TV debería estar conectada y escuchando.
            Toast.makeText(
                this,
                "Pregunta aprobada. Asegúrate de que la TV esté conectada al código: $currentClassId",
                Toast.LENGTH_LONG // Mensaje más largo para que se entienda
            ).show()

            // Opcional: Si quieres mantener la lógica de intento de lanzamiento como fallback:
            /*
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClassName("com.example.asknontv", "com.example.asknontv.ProjectionActivity")
                putExtra("CLASS_ID", currentClassId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
                Toast.makeText(this, "Intentando abrir la TV...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w(TAG_MOBILE, "No se pudo lanzar la app de TV automáticamente. Asumiendo que está abierta.", e)
                Toast.makeText(
                    this,
                    "Asegúrate de que la TV esté conectada al código: $currentClassId",
                    Toast.LENGTH_LONG
                ).show()
            }
            */

        } else {
            Toast.makeText(this, "No hay preguntas aprobadas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun projectToTvForTesting() {
        // Para pruebas en emulador
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName("com.example.asknontv", "com.example.asknontv.ProjectionActivity")
            putExtra("CLASS_ID", currentClassId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Para emulador, intenta lanzar aunque falle
        try {
            startActivity(intent)
            Toast.makeText(this, "Proyectando en TV (emulador)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Si falla, abre la app de TV de otra manera
            val genericIntent = Intent().apply {
                `package` = "com.example.asknontv"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(genericIntent)
                Toast.makeText(this, "App de TV abierta (verifica el CLASS_ID: $currentClassId)", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Instala la app de TV para proyectar. CLASS_ID: $currentClassId", Toast.LENGTH_LONG).show()
                Log.e("TeacherClassActivity", "Error al lanzar app de TV", e)
            }
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
        // Primero eliminamos todas las preguntas asociadas a la clase
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
                        // Si la eliminación de preguntas fue exitosa (o no había preguntas),
                        // procedemos a eliminar la clase
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
                        Toast.makeText(this, "Error al eliminar las preguntas de la clase: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener las preguntas para eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logoutAndGoToMain() {
        auth.signOut() // Cierra la sesión del usuario en Firebase Authentication
        // Crea un Intent para ir a MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            // Estas flags limpian el stack de actividades para que el usuario
            // no pueda volver a TeacherClassActivity presionando "atrás"
            // y aseguran que MainActivity sea la nueva tarea raíz.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish() // Cierra TeacherClassActivity
    }

    override fun onDestroy() {
        super.onDestroy()
        classListener?.remove()
        questionsListener?.remove()
    }
}