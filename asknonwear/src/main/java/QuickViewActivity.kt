package com.example.asknonwear

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class QuickViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 Obtenemos el rol desde el intent
        val rol = intent.getStringExtra("rol") ?: "desconocido"

        // 🔸 Creamos la vista dinámicamente con estilo moderno
        val tv = TextView(this).apply {
            text = when (rol) {
                "profesor" -> "📋 Tienes nuevas preguntas para revisar"
                "alumno" -> "✅ Tu pregunta fue respondida"
                else -> "👤 Bienvenido a Asknon"
            }
            textSize = 18f
            setPadding(32, 64, 32, 64)
            setTextColor(ContextCompat.getColor(this@QuickViewActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@QuickViewActivity, R.color.colorPrimary))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }

        setContentView(tv)
    }
}
