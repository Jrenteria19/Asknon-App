package com.example.asknon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuestionActionAdapter(
    // ESTA ES LA CLAVE: 'items' es una referencia a la MutableList del Activity.
    // Aunque es 'val' (la referencia no cambia), el contenido de la MutableList sí puede modificarse.
    private val items: MutableList<Pregunta>,
    private val onApprove: (Pregunta) -> Unit,
    private val onAnswer: (Pregunta) -> Unit,
    private val onReject: (Pregunta) -> Unit
) : RecyclerView.Adapter<QuestionActionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tv_question)
        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnAnswer: Button = view.findViewById(R.id.btn_answer)
        val btnReject: Button = view.findViewById(R.id.btn_reject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_question, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pregunta = items[position]
        holder.tvQuestion.text = pregunta.texto

        holder.btnApprove.setOnClickListener { onApprove(pregunta) }
        holder.btnAnswer.setOnClickListener { onAnswer(pregunta) }
        holder.btnReject.setOnClickListener { onReject(pregunta) }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    // ESTE ES EL MÉTODO CRÍTICO: Modifica el contenido de la lista, no reasigna la lista.
    fun updateData(newQuestions: List<Pregunta>) {
        items.clear() // Vacía la lista actual
        items.addAll(newQuestions) // Añade los nuevos elementos
        notifyDataSetChanged() // Notifica al RecyclerView que los datos cambiaron
    }
}