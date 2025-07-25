package com.example.asknontv

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ProjectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_projection)

        // Obtener datos enviados desde el intent
        val pregunta = intent.getStringExtra("pregunta") ?: "Esperando proyección..."
        val respuesta = intent.getStringExtra("respuesta")

        // Referencias a los TextView del layout
        val tvPregunta = findViewById<TextView>(R.id.tv_pregunta_proyectada)
        val tvRespuesta = findViewById<TextView>(R.id.tv_respuesta_proyectada)

        // Mostrar la pregunta recibida
        tvPregunta.text = pregunta

        // Mostrar la respuesta si existe
        if (!respuesta.isNullOrBlank()) {
            tvRespuesta.text = "Respuesta: $respuesta"
        } else {
            tvRespuesta.text = ""
        }
    }
}
