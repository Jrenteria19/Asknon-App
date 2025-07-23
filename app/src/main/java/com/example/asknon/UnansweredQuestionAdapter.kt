package com.example.asknon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UnansweredQuestionAdapter(
    private val items: List<FirestoreQuestion>,
    private val onMarkAsAnswered: (String) -> Unit // Callback para marcar como respondida
) : RecyclerView.Adapter<UnansweredQuestionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestionText: TextView = view.findViewById(R.id.tv_question_text) // Assuming you have this ID
        val btnMarkAsAnswered: Button = view.findViewById(R.id.btn_mark_as_answered) // Assuming you have this ID
        // You might want to add a TextView for the student's ID or a way to identify the student
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unanswered_question, parent, false) // Create this layout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = items[position]
        holder.tvQuestionText.text = question.text

        holder.btnMarkAsAnswered.setOnClickListener {
            onMarkAsAnswered(question.id) // Pass the Firestore document ID
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
