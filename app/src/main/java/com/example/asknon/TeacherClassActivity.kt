package com.example.asknon

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
    questionsListener = db.collection("preguntas")
        .whereEqualTo("claseId", currentClassId)
        .addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            snapshot?.documentChanges?.forEach { change ->
                if (change.type == DocumentChange.Type.ADDED) {
                    val pregunta = change.document.toObject(Pregunta::class.java)
                    if (pregunta.estado == "pendiente") {
                        sendNotificationToWear(pregunta.texto)
                    }
                }
            }
        }
    }

    private fun sendNotificationToWear(preguntaTexto: String) {
    // Obtén el token del Wear OS (deberías tenerlo guardado en Firestore)
    db.collection("dispositivos")
        .whereEqualTo("usuarioId", auth.currentUser?.uid)
        .limit(1)
        .get()
        .addOnSuccessListener { snapshot ->
            if (!snapshot.isEmpty) {
                val token = snapshot.documents[0].getString("tokenWear") ?: return@addOnSuccessListener
                
                // Envía la notificación usando FCM
                val notification = hashMapOf(
                    "to" to token,
                    "data" to hashMapOf(
                        "title" to "Nueva pregunta",
                        "message" to preguntaTexto,
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )

                // Usa Retrofit o HttpURLConnection para enviar la notificación
                sendFcmNotification(notification)
            }
        }
    }

    private fun sendFcmNotification(notification: HashMap<String, Any>) {
        // Implementación rápida con Retrofit (necesitas agregar la dependencia)
        val api = Retrofit.Builder()
            .baseUrl("https://fcm.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FcmApi::class.java)

        api.sendNotification(notification, "key=TU_SERVER_KEY_FCM")
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    Toast.makeText(this@TeacherClassActivity, "Notificación enviada a Wear OS", Toast.LENGTH_SHORT).show()
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@TeacherClassActivity, "Error al enviar a Wear OS", Toast.LENGTH_SHORT).show()
                }
            })
    }

    interface FcmApi {
        @POST("fcm/send")
        fun sendNotification(
            @Body notification: HashMap<String, Any>,
            @Header("Authorization") auth: String
        ): Call<ResponseBody>
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
            val pregunta = approvedQuestions.first()

            // Lanzar la app de TV con el CLASS_ID
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClassName("com.example.asknontv", "com.example.asknontv.ProjectionActivity")
                putExtra("CLASS_ID", currentClassId) // Pasar el CLASS_ID
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(intent)
                Toast.makeText(this, "Proyectando en TV", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Instala la app de TV para proyectar", Toast.LENGTH_LONG).show()
            }
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
    // Primero eliminamos todas las preguntas asociadas
    db.collection("preguntas")
        .whereEqualTo("claseId", currentClassId)
        .get()
        .addOnSuccessListener { snapshot ->
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit()
                .addOnSuccessListener {
                    // Luego eliminamos la clase
                    db.collection("clases").document(currentClassId)
                        .delete()
                        .addOnSuccessListener {
                            // Cerrar sesión
                            FirebaseAuth.getInstance().signOut()
                            
                            // Redirigir al MainActivity limpiando el stack
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                            
                            Toast.makeText(this, "Clase eliminada y sesión cerrada", Toast.LENGTH_SHORT).show()
                        }
                }
        }
        .addOnFailureListener {
            Toast.makeText(this, "Error al eliminar preguntas", Toast.LENGTH_SHORT).show()
        }
}

    override fun onDestroy() {
        super.onDestroy()
        classListener?.remove()
        questionsListener?.remove()
    }
}