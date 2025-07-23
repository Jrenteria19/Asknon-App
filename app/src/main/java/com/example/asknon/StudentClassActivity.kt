package com.example.asknon

import android.os.Bundle
import android.util.Log
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

class StudentClassActivity : AppCompatActivity() {

    private lateinit var rvQuestions: RecyclerView
    private lateinit var etInput: TextInputEditText
    private lateinit var tiInputLayout: TextInputLayout
    private lateinit var btnSend: MaterialButton
    private lateinit var adapter: QuestionAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var claseId: String? = null
    private var userId: String? = null

    private val questionList = mutableListOf<Pair<String, String?>>() // Pair: Pregunta, Respuesta (null si no ha sido respondida)
    private var questionListener: ListenerRegistration? = null // Para escuchar cambios en Firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_student)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        userId = auth.currentUser?.uid // Obtener el ID del usuario anónimo

        // Obtener el claseId del Intent
        claseId = intent.getStringExtra("claseId")
        if (claseId == null) {
            Toast.makeText(this, "Error: Clase no especificada.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Referencias a vistas
        rvQuestions = findViewById(R.id.rv_questions)
        etInput = findViewById(R.id.et_question_input)
        tiInputLayout = findViewById(R.id.ti_question_input)
        btnSend = findViewById(R.id.btn_send_question)

        // Configuración del RecyclerView
        adapter = QuestionAdapter(questionList) // El adapter ahora usa Pair
        rvQuestions.layoutManager = LinearLayoutManager(this)
        rvQuestions.adapter = adapter

        // Acción del botón "Enviar"
        btnSend.setOnClickListener {
            val text = etInput.text?.toString()?.trim().orEmpty()

            if (text.isEmpty()) {
                tiInputLayout.error = "La pregunta no puede estar vacía"
                return@setOnClickListener
            }

            if (text.length < 5) {
                tiInputLayout.error = "Escribe una pregunta más completa"
                return@setOnClickListener
            }

            // Limpia el error visual
            tiInputLayout.error = null

            // 🔹 Validar si el usuario ya tiene una pregunta pendiente
            checkAndSendQuestion(text)
        }

        // 🔹 Cargar y escuchar preguntas del usuario
        loadAndListenForQuestions()
    }

    private fun checkAndSendQuestion(questionText: String) {
        userId?.let { uid ->
            claseId?.let { classId ->
                db.collection("questions") // O la colección donde guardes las preguntas
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("claseId", classId)
                    .whereEqualTo("answered", false) // Verificar si hay preguntas sin responder
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            // 🔸 No hay preguntas pendientes, se puede enviar una nueva
                            sendQuestionToFirestore(questionText, uid, classId)
                        } else {
                            // 🔸 Ya hay una pregunta pendiente
                            Toast.makeText(this, "Ya tienes una pregunta pendiente de respuesta.", Toast.LENGTH_SHORT).show()
                            disableInput() // Deshabilitar entrada si hay pregunta pendiente
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("StudentClassActivity", "Error al verificar preguntas pendientes", e)
                        Toast.makeText(this, "Error al verificar preguntas.", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun sendQuestionToFirestore(questionText: String, userId: String, claseId: String) {
        val question = hashMapOf(
            "text" to questionText,
            "userId" to userId,
            "claseId" to claseId,
            "timestamp" to System.currentTimeMillis(), // O usa un Timestamp de Firebase
            "answered" to false,
            "answer" to null // Campo para la respuesta, inicialmente nulo
        )

        db.collection("questions") // O la colección donde guardes las preguntas
            .add(question)
            .addOnSuccessListener {
                Toast.makeText(this, "Pregunta enviada.", Toast.LENGTH_SHORT).show()
                etInput.text?.clear() // Limpiar el campo de entrada
                disableInput() // Deshabilitar entrada después de enviar
            }
            .addOnFailureListener { e ->
                Log.e("StudentClassActivity", "Error al enviar pregunta", e)
                Toast.makeText(this, "Error al enviar pregunta.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAndListenForQuestions() {
        userId?.let { uid ->
            claseId?.let { classId ->
                // Consultar solo las preguntas de este usuario en esta clase, ordenadas por tiempo
                questionListener = db.collection("questions")
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("claseId", classId)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null) {
                            Log.w("StudentClassActivity", "Listen failed.", e)
                            Toast.makeText(this, "Error al cargar preguntas.", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }

                        if (snapshots != null) {
                            questionList.clear()
                            for (doc in snapshots) {
                                val text = doc.getString("text") ?: ""
                                val answer = doc.getString("answer") // Obtener la respuesta
                                questionList.add(Pair(text, answer))
                            }
                            adapter.notifyDataSetChanged()

                            // Verificar si hay preguntas pendientes y actualizar el estado del input
                            val hasPendingQuestion = questionList.any { it.second == null } // Buscar preguntas con respuesta nula
                            if (hasPendingQuestion) {
                                disableInput()
                            } else {
                                enableInput()
                            }
                        }
                    }
            }
        }
    }

    private fun disableInput() {
        tiInputLayout.isEnabled = false
        etInput.isEnabled = false
        btnSend.isEnabled = false
        tiInputLayout.hint = "Espera la respuesta a tu pregunta" // O un mensaje similar
    }

    private fun enableInput() {
        tiInputLayout.isEnabled = true
        etInput.isEnabled = true
        btnSend.isEnabled = true
        tiInputLayout.hint = "Escribe tu pregunta" // Restaurar el hint original
    }

    override fun onDestroy() {
        super.onDestroy()
        // 🔹 Detener el listener para evitar fugas de memoria
        questionListener?.remove()
    }
}
