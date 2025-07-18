package com.example.asknon

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class StudentClassActivity : AppCompatActivity() {

    private lateinit var rvQuestions: RecyclerView
    private lateinit var etInput: TextInputEditText
    private lateinit var tiInputLayout: TextInputLayout
    private lateinit var btnSend: MaterialButton
    private lateinit var adapter: QuestionAdapter

    private val questionList = mutableListOf<String>() // Lista local de preguntas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_student)

        // Referencias a vistas
        rvQuestions = findViewById(R.id.rv_questions)
        etInput = findViewById(R.id.et_question_input)
        tiInputLayout = findViewById(R.id.ti_question_input)
        btnSend = findViewById(R.id.btn_send_question)

        // Configuración del RecyclerView
        adapter = QuestionAdapter(questionList)
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

            // Simula el envío
            questionList.add(0, text)
            adapter.notifyItemInserted(0)
            rvQuestions.scrollToPosition(0)
            etInput.text?.clear()

            Toast.makeText(this, "Pregunta enviada", Toast.LENGTH_SHORT).show()
        }
    }
}
