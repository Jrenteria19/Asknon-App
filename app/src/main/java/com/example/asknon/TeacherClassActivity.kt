package com.example.asknon

import ShakeDetector
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.WriteBatch
import java.util.Calendar
import java.util.Date
import java.util.UUID
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Pregunta(
    val id: String = "",
    val texto: String = "",
    val respuesta: String? = null,
    val estado: String = "pendiente", // "pendiente", "aprobada", "rechazada"
    val claseId: String = "",
    val estudianteId: String = "",
    val fechaCreacion: Date = Date()
)

class TeacherClassActivity : AppCompatActivity() {
    // Elementos de UI
    private lateinit var tvClassCode: TextView
    private lateinit var rvPending: RecyclerView
    private lateinit var rvApproved: RecyclerView
    private lateinit var btnProject: Button
    private lateinit var btnDeleteClass: Button

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var questionsListener: ListenerRegistration? = null

    // Datos
    private var currentClassId: String = ""
    private val pendingQuestions = mutableListOf<Pregunta>()
    private val approvedQuestions = mutableListOf<Pregunta>()

    // Adaptadores
    private lateinit var pendingAdapter: QuestionActionAdapter
    private lateinit var approvedAdapter: PreguntaAdapter

    // --- Inicio: Código para el sensor de agitación ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var shakeDetector: ShakeDetector
    // --- Fin: Código para el sensor de agitación ---

    companion object {
        private const val TAG = "TeacherClassActivity" // Para logs
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_teacher)
        initViews()
        setupAdapters()
        checkExistingClass()

        // --- Inicio: Inicialización del detector de agitación ---
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        shakeDetector = ShakeDetector {
            // Acción que se ejecuta al agitar el teléfono
            Log.d(TAG, "Teléfono agitado! Aprobando todas las preguntas pendientes.")
            approveAllPendingQuestionsInFirestore()
        }
        // --- Fin: Inicialización del detector de agitación ---
    }

    // --- Inicio: Ciclo de vida para el sensor ---
    override fun onResume() {
        super.onResume()
        // Registra el listener del sensor cuando la actividad es visible
        accelerometer?.let {
            sensorManager.registerListener(shakeDetector, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Desregistra el listener para ahorrar batería cuando la actividad no está visible
        sensorManager.unregisterListener(shakeDetector)
    }
    // --- Fin: Ciclo de vida para el sensor ---


    private fun initViews() {
        tvClassCode = findViewById(R.id.tv_class_code)
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

    // --- Inicio: Nueva función para aprobar todas las preguntas ---
    private fun approveAllPendingQuestionsInFirestore() {
        if (currentClassId.isEmpty()) {
            Toast.makeText(this, "ID de clase no disponible para aprobar preguntas.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .whereEqualTo("estado", "pendiente")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No hay preguntas pendientes para aprobar.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                snapshot.documents.forEach { doc ->
                    batch.update(doc.reference, "estado", "aprobada")
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Todas las preguntas pendientes han sido aprobadas.", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Lote de aprobación de preguntas completado exitosamente.")
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error al aprobar todas las preguntas.", Toast.LENGTH_SHORT).show()
                        Log.e(TAG, "Error al ejecutar el lote de aprobación de preguntas.", e)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener las preguntas pendientes.", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error al obtener preguntas para aprobarlas todas.", e)
            }
    }
    // --- Fin: Nueva función para aprobar todas las preguntas ---

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
                    setupClassInfo(clase.getString("codigo") ?: "")
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

        val nuevaClase = hashMapOf(
            "codigo" to classCode,
            "profesorId" to profesorId,
            "fechaCreacion" to Calendar.getInstance().time
        )
        db.collection("clases").document(classId)
            .set(nuevaClase)
            .addOnSuccessListener {
                currentClassId = classId
                setupClassInfo(classCode)
                setupQuestionsListener()
                Toast.makeText(this, "Clase creada exitosamente", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al crear clase", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupClassInfo(classCode: String) {
        tvClassCode.text = "Código: $classCode\nID: $currentClassId"
    }

    private fun generateUniqueClassCode(): String {

        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        return (1..6).map { chars.random() }.joinToString("")

    }

    private fun setupQuestionsListener() {
        if (currentClassId.isEmpty()) return
        questionsListener = db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error al cargar preguntas", error)
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
                pendingAdapter.updateData(newPendingQuestions)
                approvedAdapter.updateData(newApprovedQuestions)

                sendPendingCountToWatch(newPendingQuestions.size)

            }
    }

    private fun sendPendingCountToWatch(count: Int) {
        Log.d(TAG, "Intentando enviar conteo al reloj: $count")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Buscamos los nodos (relojes) conectados
                val nodes = Wearable.getNodeClient(applicationContext).connectedNodes.await()
                nodes.forEach { node ->
                    // Definimos un "path" o canal para este tipo de mensaje
                    val path = "/pending_questions_count"
                    // Convertimos el conteo a bytes para poder enviarlo
                    val payload = count.toString().toByteArray()

                    // Enviamos el mensaje
                    Wearable.getMessageClient(applicationContext).sendMessage(
                        node.id,
                        path,
                        payload
                    ).await()
                    Log.d(TAG, "Conteo enviado exitosamente al nodo: ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al enviar el conteo al reloj", e)
            }
        }
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
                "Asegúrate de que la TV esté conectada al código: $currentClassId",
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
                val batch: WriteBatch = db.batch()
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
                        Toast.makeText(this, "Error al eliminar las preguntas de la clase: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener las preguntas para eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
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
        questionsListener?.remove()
    }
}