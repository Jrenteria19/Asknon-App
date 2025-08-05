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
    private lateinit var etCode: TextInputEditText
    private lateinit var inputLayout: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_class)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        inputLayout = findViewById(R.id.text_input_layout)
        etCode = findViewById(R.id.et_class_code)
        val btnEnter = findViewById<MaterialButton>(R.id.btn_enter)

        // Se elimina la referencia y la lógica del botón de escaneo QR
        // val btnScanQR = findViewById<MaterialButton>(R.id.btn_scan_qr)

        verificarRol { rol ->
            if (rol == "profesor") {
                startActivity(Intent(this, TeacherClassActivity::class.java))
                finish()
                return@verificarRol
            }

            // El botón de entrar mantiene su funcionalidad
            btnEnter.setOnClickListener {
                val code = etCode.text?.toString()?.trim().orEmpty()
                validarCodigoAntesDeUnirse(code)
            }
        }
    }

    private fun validarCodigoAntesDeUnirse(code: String) {
        when {
            code.isEmpty() -> {
                inputLayout.error = "Este campo no puede estar vacío"
                Toast.makeText(this, "Por favor ingresa un código de clase", Toast.LENGTH_SHORT).show()
            }
            !code.matches(Regex("^[a-zA-Z0-9]{4,8}$")) -> {
                inputLayout.error = "El código debe ser alfanumérico (4-8 caracteres)"
                Toast.makeText(this, "Formato de código inválido", Toast.LENGTH_SHORT).show()
            }
            else -> {
                inputLayout.error = null
                validarYUnirseAClase(code)
            }
        }
    }

    private fun verificarRol(callback: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Usuario no autenticado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                when (val rol = doc.getString("rol")) {
                    "alumno", "profesor" -> callback(rol)
                    else -> {
                        Toast.makeText(this, "Debes seleccionar un rol primero", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al verificar rol de usuario", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun validarYUnirseAClase(codigoClase: String) {
        Toast.makeText(this, "Validando código de clase...", Toast.LENGTH_SHORT).show()

        db.collection("clases")
            .whereEqualTo("codigo", codigoClase.uppercase()) // Se busca por el código en mayúsculas para ser consistente
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "La clase no existe o el código es incorrecto", Toast.LENGTH_LONG).show()
                    inputLayout.error = "Código inválido"
                } else {
                    val claseDoc = querySnapshot.documents[0]
                    val claseId = claseDoc.id

                    if (claseId.isNotEmpty()) {
                        unirseAClase(claseId)
                    } else {
                        Toast.makeText(this, "Error: No se pudo obtener el ID de la clase.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al conectar con la base de datos: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun unirseAClase(claseId: String) {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Error de autenticación", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(userId)
            .update("claseId", claseId)
            .addOnSuccessListener {
                Toast.makeText(this, "Te has unido a la clase exitosamente", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, StudentClassActivity::class.java).apply {
                    putExtra("CLASS_ID", claseId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al unirse a la clase: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}