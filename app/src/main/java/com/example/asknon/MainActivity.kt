package com.example.asknon

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Toast.makeText(this, "Bienvenido a Asknon", Toast.LENGTH_SHORT).show()

        val cardAlumno = findViewById<MaterialCardView>(R.id.card_student)
        val cardDocente = findViewById<MaterialCardView>(R.id.card_teacher)

        cardAlumno.setOnClickListener {
            val intent = Intent(this, JoinClassActivity::class.java)
            intent.putExtra("rol", "alumno")
            startActivity(intent)
        }

        cardDocente.setOnClickListener {
            val intent = Intent(this, JoinClassActivity::class.java)
            intent.putExtra("rol", "profesor")
            startActivity(intent)
        }
    }
}
