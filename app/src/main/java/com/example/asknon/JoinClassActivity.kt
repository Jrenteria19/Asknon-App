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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class JoinClassActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var etCode: TextInputEditText
    private lateinit var inputLayout: TextInputLayout

    // Registra el lanzador de la actividad de escaneo
    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // Procesa el resultado del escaneo
            val scannedCode = result.contents.trim()
            etCode.setText(scannedCode)

            // Validar automáticamente el código escaneado
            validarYUnirseAClase(scannedCode)
        } else {
            Toast.makeText(this, "Escaneo de QR cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_class)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        inputLayout = findViewById(R.id.text_input_layout)
        etCode = findViewById(R.id.et_class_code)
        val btnScanQR = findViewById<MaterialButton>(R.id.btn_scan_qr)
        val btnEnter = findViewById<MaterialButton>(R.id.btn_enter)

        verificarRol { rol ->
            if (rol == "profesor") {
                startActivity(Intent(this, TeacherClassActivity::class.java))
                finish()
                return@verificarRol
            }

            // Configuración del botón de escaneo QR
            btnScanQR.setOnClickListener {
                iniciarEscaneoQR()
            }

            btnEnter.setOnClickListener {
                val code = etCode.text?.toString()?.trim().orEmpty()
                validarCodigoAntesDeUnirse(code)
            }
        }
    }

    private fun iniciarEscaneoQR() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Escanea el código QR de la clase")
            setCameraId(0) // Usa cámara trasera
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
            setTimeout(15000) // 15 segundos de timeout
        }

        qrScannerLauncher.launch(options)
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
        // Mostrar progreso
        Toast.makeText(this, "Validando código de clase...", Toast.LENGTH_SHORT).show()

        db.collection("clases")
            .whereEqualTo("codigo", codigoClase)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "La clase no existe o el código es incorrecto", Toast.LENGTH_LONG).show()
                    inputLayout.error = "Código inválido"
                } else {
                    val claseDoc = querySnapshot.documents[0]
                    if (claseDoc.exists()) {
                        unirseAClase(claseDoc.id)
                    } else {
                        Toast.makeText(this, "Error al acceder a los datos de la clase", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al conectar con la base de datos", Toast.LENGTH_SHORT).show()
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
                startActivity(Intent(this, StudentClassActivity::class.java).apply {
                    putExtra("claseId", claseId)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al unirse a la clase", Toast.LENGTH_SHORT).show()
            }
    }
}