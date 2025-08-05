package com.example.asknon

import android.content.Intent
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

class StudentClassActivity : AppCompatActivity() {

    private lateinit var rvQuestions: RecyclerView
    private lateinit var etInput: TextInputEditText
    private lateinit var tiInputLayout: TextInputLayout
    private lateinit var btnSend: MaterialButton

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    // Dos listeners: uno para las preguntas y otro para el estado de la clase
    private var questionsListener: ListenerRegistration? = null
    private var classListener: ListenerRegistration? = null

    // Datos del estudiante y la clase actual
    private var currentStudentId: String = ""
    private var currentClassId: String = ""

    // Adaptador
    private lateinit var adapter: StudentQuestionAdapter
    private val questionList = mutableListOf<Pregunta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_student)

        currentClassId = intent.getStringExtra("CLASS_ID") ?: ""

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
        // Iniciamos ambos listeners
        setupQuestionsListener()
        setupClassListener() // <-- NUEVO: Listener para detectar si la clase se elimina
    }

    private fun initViews() {
        rvQuestions = findViewById(R.id.rv_questions)
        etInput = findViewById(R.id.et_question_input)
        tiInputLayout = findViewById(R.id.ti_question_input)
        btnSend = findViewById(R.id.btn_send_question)

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
        tiInputLayout.error = null

        val newQuestion = Pregunta(
            id = UUID.randomUUID().toString(),
            texto = text,
            estado = "pendiente",
            claseId = currentClassId,
            estudianteId = currentStudentId,
            fechaCreacion = Calendar.getInstance().time
        )

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
                    doc.toObject(Pregunta::class.java)?.let {
                        updatedQuestions.add(it.copy(id = doc.id))
                    }
                }

                updatedQuestions.sortByDescending { it.fechaCreacion }
                questionList.clear()
                questionList.addAll(updatedQuestions)
                adapter.notifyDataSetChanged()

                if (updatedQuestions.isNotEmpty() && (rvQuestions.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() != 0) {
                    rvQuestions.scrollToPosition(0)
                }
            }
    }

    /**
     * NUEVO: Listener que vigila el documento de la clase. Si el documento
     * es eliminado, significa que el profesor terminó la clase y debemos
     * redirigir al estudiante.
     */
    private fun setupClassListener() {
        // Aseguramos que tenemos un ID de clase antes de escuchar
        if (currentClassId.isEmpty()) return

        classListener = db.collection("clases").document(currentClassId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Podríamos ignorar este error para no molestar al usuario,
                    // ya que la funcionalidad principal sigue siendo la de las preguntas.
                    // Opcionalmente, registrar el error.
                    return@addSnapshotListener
                }

                // Si el snapshot no existe, la clase fue eliminada.
                if (snapshot == null || !snapshot.exists()) {
                    // Detenemos los listeners para no seguir trabajando en segundo plano
                    classListener?.remove()
                    questionsListener?.remove()

                    Toast.makeText(this, "La clase ha sido cerrada por el profesor.", Toast.LENGTH_LONG).show()

                    // Creamos un Intent para ir a la actividad de unirse a una clase
                    // Reemplaza 'JoinClassActivity::class.java' por la actividad correcta
                    val intent = Intent(this, JoinClassActivity::class.java).apply {
                        // Limpiamos el historial de actividades para que el usuario no pueda
                        // volver a esta pantalla con el botón "atrás".
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish() // Cierra esta actividad
                }
            }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Detenemos ambos listeners cuando la actividad se destruye
        questionsListener?.remove()
        classListener?.remove()
    }
}