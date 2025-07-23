package com.example.asknon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AnsweredQuestionAdapter(
    private val items: List<FirestoreQuestion>
) : RecyclerView.Adapter<AnsweredQuestionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestionText: TextView = view.findViewById(R.id.tv_question_text) // Assuming you have this ID
        // You might want to show the answer here if applicable, but the request only asks to mark as answered
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_answered_question, parent, false) // Create this layout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = items[position]
        holder.tvQuestionText.text = question.text
        // You could add logic here to show the answer if question.answer is not null
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
