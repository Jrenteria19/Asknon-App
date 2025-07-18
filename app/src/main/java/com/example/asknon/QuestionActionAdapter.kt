package com.example.asknon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuestionActionAdapter(
    private val items: List<String>,
    private val onApprove: (String) -> Unit,
    private val onAnswer: (String) -> Unit,
    private val onReject: (String) -> Unit
) : RecyclerView.Adapter<QuestionActionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tv_question)
        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnAnswer: Button = view.findViewById(R.id.btn_answer)
        val btnReject: Button = view.findViewById(R.id.btn_reject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_question_actions, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val question = items[position]
        holder.tvQuestion.text = question

        holder.btnApprove.setOnClickListener { onApprove(question) }
        holder.btnAnswer.setOnClickListener { onAnswer(question) }
        holder.btnReject.setOnClickListener { onReject(question) }
    }

    override fun getItemCount(): Int = items.size
}
