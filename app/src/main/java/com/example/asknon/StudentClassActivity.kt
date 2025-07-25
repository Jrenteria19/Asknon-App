package com.example.asknon

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

// Reutilizamos la misma clase Pregunta de TeacherClassActivity
// data class Pregunta(...)

class StudentClassActivity : AppCompatActivity() {

    private lateinit var rvQuestions: RecyclerView
    private lateinit var etInput: TextInputEditText
    private lateinit var tiInputLayout: TextInputLayout
    private lateinit var btnSend: MaterialButton

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var questionsListener: ListenerRegistration? = null

    // Datos del estudiante y la clase actual
    private var currentStudentId: String = ""
    private var currentClassId: String = "" // Necesitaremos obtener este ID, por ejemplo, del código QR escaneado
    // o pasado desde otra actividad. Por ahora, lo dejaremos vacío.

    // Adaptador
    private lateinit var adapter: StudentQuestionAdapter // Cambiado a un adaptador específico para estudiantes
    private val questionList = mutableListOf<Pregunta>() // Lista local de objetos Pregunta

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_student)

        // **IMPORTANTE**: Necesitas una forma de obtener el 'classId' y el 'studentId' (uid del usuario actual)
        // Por ahora, asumiremos que 'classId' se pasa como un extra en el Intent
        currentClassId = intent.getStringExtra("CLASS_ID") ?: ""

        // Si no tenemos un CLASS_ID, no podemos continuar.
        if (currentClassId.isEmpty()) {
            Toast.makeText(this, "Error: No se ha proporcionado un ID de clase.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentStudentId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupQuestionsListener() // Inicia el listener para recibir actualizaciones de preguntas
    }

    private fun initViews() {
        rvQuestions = findViewById(R.id.rv_questions)
        etInput = findViewById(R.id.et_question_input)
        tiInputLayout = findViewById(R.id.ti_question_input)
        btnSend = findViewById(R.id.btn_send_question)

        // Acción del botón "Enviar"
        btnSend.setOnClickListener {
            sendQuestion()
        }
    }

    private fun setupRecyclerView() {
        adapter = StudentQuestionAdapter(questionList)
        rvQuestions.layoutManager = LinearLayoutManager(this)
        rvQuestions.adapter = adapter
    }

    private fun sendQuestion() {
        val text = etInput.text?.toString()?.trim().orEmpty()

        if (text.isEmpty()) {
            tiInputLayout.error = "La pregunta no puede estar vacía"
            return
        }

        if (text.length < 5) {
            tiInputLayout.error = "Escribe una pregunta más completa"
            return
        }

        tiInputLayout.error = null // Limpia el error visual

        val newQuestion = Pregunta(
            id = UUID.randomUUID().toString(), // Genera un ID único para la pregunta
            texto = text,
            estado = "pendiente", // Las preguntas de los estudiantes inician como pendientes
            claseId = currentClassId,
            estudianteId = currentStudentId,
            fechaCreacion = Calendar.getInstance().time
        )

        // Guarda la pregunta en Firestore
        db.collection("preguntas").document(newQuestion.id)
            .set(newQuestion)
            .addOnSuccessListener {
                Toast.makeText(this, "Pregunta enviada", Toast.LENGTH_SHORT).show()
                etInput.text?.clear()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al enviar pregunta: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupQuestionsListener() {
        questionsListener = db.collection("preguntas")
            .whereEqualTo("claseId", currentClassId)
            .whereEqualTo("estudianteId", currentStudentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error al cargar tus preguntas: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val updatedQuestions = mutableListOf<Pregunta>()
                snapshot?.documents?.forEach { doc ->
                    val preguntaOriginal = doc.toObject(Pregunta::class.java)
                    if (preguntaOriginal != null) {
                        // Crea una nueva instancia de Pregunta con el id del documento
                        val preguntaConIdCorrecto = preguntaOriginal.copy(id = doc.id)
                        updatedQuestions.add(preguntaConIdCorrecto)
                    }
                }

                // Ordena las preguntas por fecha de creación (las más recientes primero)
                updatedQuestions.sortByDescending { it.fechaCreacion }

                // Actualiza el adaptador
                // Asegúrate que tu adaptador tiene un método como updateData o que puedes reasignar la lista
                // y luego llamar a notifyDataSetChanged()
                questionList.clear() // Limpia la lista antigua
                questionList.addAll(updatedQuestions) // Añade las nuevas preguntas
                adapter.notifyDataSetChanged() // Notifica al adaptador


                if (updatedQuestions.isNotEmpty() && (questionList.isEmpty() ||
                            (questionList.isNotEmpty() && updatedQuestions.first().id != questionList.first().id))) {
                    rvQuestions.scrollToPosition(0)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        questionsListener?.remove() // Detener el listener cuando la actividad se destruye
    }
}