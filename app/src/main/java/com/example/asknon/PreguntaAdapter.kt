package com.example.asknon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Adaptador para mostrar preguntas con su respuesta
class PreguntaAdapter(
    private val items: List<Pregunta>
) : RecyclerView.Adapter<PreguntaAdapter.PreguntaViewHolder>() {

    // ViewHolder para representar visualmente una pregunta + respuesta
    class PreguntaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPregunta: TextView = view.findViewById(R.id.tv_pregunta_texto)
        val tvRespuesta: TextView = view.findViewById(R.id.tv_pregunta_respuesta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreguntaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pregunta, parent, false)
        return PreguntaViewHolder(view)
    }

    override fun onBindViewHolder(holder: PreguntaViewHolder, position: Int) {
        val pregunta = items[position]
        holder.tvPregunta.text = "❓ ${pregunta.texto}"
        holder.tvRespuesta.text = if (!pregunta.respuesta.isNullOrEmpty()) {
            "💬 ${pregunta.respuesta}"
        } else {
            "⌛ Aún sin respuesta"
        }
    }

    override fun getItemCount(): Int = items.size
}
