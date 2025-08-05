package com.example.asknon

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            iniciarSesionAnonimo()
        } else {
            verificarRolYRedirigir()
        }

        findViewById<MaterialCardView>(R.id.card_student).setOnClickListener {
            guardarRolYRedirigir("alumno")
        }

        findViewById<MaterialCardView>(R.id.card_teacher).setOnClickListener {
            guardarRolYRedirigir("profesor")
        }
    }

    private fun iniciarSesionAnonimo() {
        auth.signInAnonymously().addOnSuccessListener {
            Toast.makeText(this, "Sesión iniciada", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(this, "Error al iniciar sesión", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarRolYRedirigir(rol: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).set(mapOf("rol" to rol))
            .addOnSuccessListener {
                redirigirSegunRol(rol)
            }
    }

    private fun verificarRolYRedirigir() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                doc.getString("rol")?.let { rol ->
                    redirigirSegunRol(rol)
                }
            }
    }

    private fun redirigirSegunRol(rol: String) {
        val intent = when (rol) {
            "profesor" -> Intent(this, TeacherClassActivity::class.java)
            else -> Intent(this, JoinClassActivity::class.java) // Alumno va a JoinClass
        }
        startActivity(intent)
        finish()
    }
}