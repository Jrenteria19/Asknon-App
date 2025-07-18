package com.example.asknon

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class JoinClassActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_class)

        val inputLayout = findViewById<TextInputLayout>(R.id.text_input_layout)
        val etCode = findViewById<TextInputEditText>(R.id.et_class_code)
        val btnScanQR = findViewById<MaterialButton>(R.id.btn_scan_qr)
        val btnEnter = findViewById<MaterialButton>(R.id.btn_enter)
        val rol = intent.getStringExtra("rol") ?: "alumno"

        // 🔹 Si es profesor, redirige automáticamente a su pantalla
        if (rol == "profesor") {
            startActivity(Intent(this, TeacherClassActivity::class.java))
            finish()
            return
        }

        // 🔸 Alumno: escaneo simulado
        btnScanQR.setOnClickListener {
            Toast.makeText(this, "Escaneo QR aún no implementado", Toast.LENGTH_SHORT).show()
        }

        btnEnter.setOnClickListener {
            val code = etCode.text?.toString()?.trim().orEmpty()

            // Validaciones visuales usando TextInputLayout
            when {
                code.isEmpty() -> {
                    inputLayout.error = "Este campo no puede estar vacío"
                }

                !code.matches(Regex("^[a-zA-Z0-9]{4,8}$")) -> {
                    inputLayout.error = "El código debe ser alfanumérico (4-8 caracteres)"
                }

                else -> {
                    inputLayout.error = null // Limpia errores
                    val intentAlumno = Intent(this, StudentClassActivity::class.java)
                    intentAlumno.putExtra("codigoClase", code)
                    startActivity(intentAlumno)
                }
            }
        }
    }
}
