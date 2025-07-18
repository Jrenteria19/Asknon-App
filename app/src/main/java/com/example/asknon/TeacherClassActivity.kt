package com.example.asknon

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// Data class que representa una pregunta con una posible respuesta
// Esta estructura permite mostrar tanto la pregunta como la respuesta aprobada
data class Pregunta(val texto: String, val respuesta: String?)

class TeacherClassActivity : AppCompatActivity() {

    // Elementos de UI
    private lateinit var tvClassCode: TextView
    private lateinit var rvPending: RecyclerView
    private lateinit var rvApproved: RecyclerView
    private lateinit var btnProject: Button

    // Adaptadores para listas
    private lateinit var pendingAdapter: QuestionActionAdapter
    private lateinit var approvedAdapter: PreguntaAdapter

    // Listas de preguntas
    private val pendingList = mutableListOf(
        "¿Qué entra en el examen?",
        "¿Cuándo entregamos?",
        "¿Podemos usar calculadora?"
    )
    private val approvedList = mutableListOf<Pregunta>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_teacher)

        // Vincular vistas del layout
        tvClassCode = findViewById(R.id.tv_class_code)
        rvPending = findViewById(R.id.rv_pending_questions)
        rvApproved = findViewById(R.id.rv_approved_questions)
        btnProject = findViewById(R.id.btn_project_tv)

        // Generar código aleatorio para la clase
        tvClassCode.text = "Código: ${generarCodigoClase()}"

        // Adaptador con acciones para cada pregunta pendiente
        pendingAdapter = QuestionActionAdapter(
            items = pendingList,
            onApprove = { question ->
                // Agrega pregunta aprobada sin respuesta
                approvedList.add(0, Pregunta(question, null))
                pendingList.remove(question)
                refreshAdapters()
                Toast.makeText(this, "✅ Pregunta aprobada", Toast.LENGTH_SHORT).show()
            },
            onAnswer = { question ->
                // Lanza diálogo para responder la pregunta
                mostrarDialogoResponder(question)
            },
            onReject = { question ->
                // Elimina pregunta de la lista
                pendingList.remove(question)
                refreshAdapters()
                Toast.makeText(this, "❌ Pregunta rechazada", Toast.LENGTH_SHORT).show()
            }
        )

        // Adaptador para preguntas aprobadas con respuestas
        approvedAdapter = PreguntaAdapter(approvedList)

        // Configurar RecyclerView de pendientes
        rvPending.layoutManager = LinearLayoutManager(this)
        rvPending.adapter = pendingAdapter

        // Configurar RecyclerView de aprobadas
        rvApproved.layoutManager = LinearLayoutManager(this)
        rvApproved.adapter = approvedAdapter

        // Acción para botón de proyectar
        btnProject.setOnClickListener {
            if (approvedList.isNotEmpty()) {
                val pregunta = approvedList.first()
                Toast.makeText(this, "📺 Proyectando: \"${pregunta.texto}\"", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ No hay preguntas aprobadas", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Muestra un AlertDialog para ingresar respuesta a una pregunta
    private fun mostrarDialogoResponder(preguntaTexto: String) {
        val input = EditText(this).apply {
            hint = "Escribe tu respuesta"
        }

        AlertDialog.Builder(this)
            .setTitle("Responder pregunta")
            .setMessage(preguntaTexto)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val respuesta = input.text.toString().trim()
                if (respuesta.isNotEmpty()) {
                    approvedList.add(0, Pregunta(preguntaTexto, respuesta))
                    pendingList.remove(preguntaTexto)
                    refreshAdapters()
                    Toast.makeText(this, "💬 Respuesta guardada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "⚠️ Respuesta vacía", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Refresca ambos adaptadores de preguntas
    private fun refreshAdapters() {
        pendingAdapter.notifyDataSetChanged()
        approvedAdapter.notifyDataSetChanged()
    }

    // Genera un código aleatorio de 6 caracteres para la clase
    private fun generarCodigoClase(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}