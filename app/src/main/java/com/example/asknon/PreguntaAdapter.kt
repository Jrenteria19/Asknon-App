package com.example.asknon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PreguntaAdapter(
    private val items: MutableList<Pregunta>,
    private val onDelete: (Pregunta) -> Unit
) : RecyclerView.Adapter<PreguntaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tv_question)
        val tvAnswer: TextView = view.findViewById(R.id.tv_answer)
        val btnDelete: Button = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_approved_question, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pregunta = items[position]
        holder.tvQuestion.text = pregunta.texto
        if (pregunta.respuesta != null && pregunta.respuesta.isNotEmpty()) {
            holder.tvAnswer.text = "Respuesta: ${pregunta.respuesta}"
            holder.tvAnswer.visibility = View.VISIBLE
        } else {
            holder.tvAnswer.visibility = View.GONE
        }

        holder.btnDelete.visibility = View.VISIBLE
        holder.btnDelete.setOnClickListener { onDelete(pregunta) }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    // ESTE ES EL MÉTODO CRÍTICO: Modifica el contenido de la lista, no reasigna la lista.
    fun updateData(newQuestions: List<Pregunta>) {
        items.clear()
        items.addAll(newQuestions)
        notifyDataSetChanged()
    }
}