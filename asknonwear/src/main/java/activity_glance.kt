package com.example.asknonwear

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity

class GlanceActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glance)

        val rol = intent.getStringExtra("rol") ?: "alumno"
        val title = findViewById<TextView>(R.id.tv_title)
        val summary = findViewById<TextView>(R.id.tv_summary)
        val icon = findViewById<ImageView>(R.id.img_icon)

        if (rol == "profesor") {
            title.text = "Clase en Curso"
            summary.text = "Tienes 3 preguntas pendientes"
            icon.setImageResource(R.drawable.ic_teacher)
        } else {
            title.text = "Tu Participación"
            summary.text = "Tu pregunta fue respondida 🎉"
            icon.setImageResource(R.drawable.ic_student)
        }
    }
}
