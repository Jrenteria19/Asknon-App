package com.example.asknon

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StudentQuestionAdapter(
    private val items: MutableList<Pregunta>
) : RecyclerView.Adapter<StudentQuestionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestionText: TextView = view.findViewById(R.id.tv_student_question_text)
        val tvQuestionStatus: TextView = view.findViewById(R.id.tv_student_question_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_question, parent, false) // Usaremos un nuevo layout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pregunta = items[position]
        holder.tvQuestionText.text = pregunta.texto

        // Muestra el estado de la pregunta
        when (pregunta.estado) {
            "pendiente" -> {
                holder.tvQuestionStatus.text = "Estado: Pendiente"
                holder.tvQuestionStatus.setTextColor(Color.parseColor("#FFA000")) // Naranja
            }
            "aprobada" -> {
                holder.tvQuestionStatus.text = "Estado: Aprobada"
                holder.tvQuestionStatus.setTextColor(Color.parseColor("#4CAF50")) // Verde
            }
            "rechazada" -> {
                holder.tvQuestionStatus.text = "Estado: Rechazada"
                holder.tvQuestionStatus.setTextColor(Color.parseColor("#D32F2F")) // Rojo
            }
        }

    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun updateData(newQuestions: List<Pregunta>) {
        items.clear()
        items.addAll(newQuestions)
        notifyDataSetChanged()
    }
}