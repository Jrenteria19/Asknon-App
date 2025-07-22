package com.example.asknon

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class JoinClassActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_class)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val inputLayout = findViewById<TextInputLayout>(R.id.text_input_layout)
        val etCode = findViewById<TextInputEditText>(R.id.et_class_code)
        val btnScanQR = findViewById<MaterialButton>(R.id.btn_scan_qr)
        val btnEnter = findViewById<MaterialButton>(R.id.btn_enter)

        // 🔹 Verificación de rol (seguridad redundante)
        verificarRol { rol ->
            if (rol == "profesor") {
                startActivity(Intent(this, TeacherClassActivity::class.java))
                finish()
                return@verificarRol
            }

            // 🔸 Lógica para alumnos
            btnScanQR.setOnClickListener {
                Toast.makeText(this, "Escaneo QR aún no implementado", Toast.LENGTH_SHORT).show()
            }

            btnEnter.setOnClickListener {
                val code = etCode.text?.toString()?.trim().orEmpty()

                when {
                    code.isEmpty() -> inputLayout.error = "Este campo no puede estar vacío"
                    !code.matches(Regex("^[a-zA-Z0-9]{4,8}$")) -> {
                        inputLayout.error = "El código debe ser alfanumérico (4-8 caracteres)"
                    }
                    else -> {
                        inputLayout.error = null
                        validarYUnirseAClase(code)
                    }
                }
            }
        }
    }

    private fun verificarRol(callback: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            finish()
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                when (val rol = doc.getString("rol")) {
                    "alumno", "profesor" -> callback(rol)
                    else -> { // Rol no definido
                        Toast.makeText(this, "Debes seleccionar un rol primero", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al verificar rol", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun validarYUnirseAClase(codigoClase: String) {
        db.collection("clases")
            .whereEqualTo("codigo", codigoClase)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "Código inválido", Toast.LENGTH_SHORT).show()
                } else {
                    // 🔹 Actualiza el documento del usuario con la clase
                    val userId = auth.currentUser?.uid ?: return@addOnSuccessListener
                    val claseId = querySnapshot.documents[0].id

                    db.collection("users").document(userId)
                        .update("claseId", claseId)
                        .addOnSuccessListener {
                            // 🔹 Redirige al alumno a su pantalla principal
                            startActivity(Intent(this, StudentClassActivity::class.java).apply {
                                putExtra("claseId", claseId)
                            })
                                finish()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al validar clase", Toast.LENGTH_SHORT).show()
            }
    }
}