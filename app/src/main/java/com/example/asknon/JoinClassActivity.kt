package com.example.asknon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class JoinClassActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var inputLayout: TextInputLayout
    private lateinit var etCode: TextInputEditText
    private lateinit var btnScanQR: MaterialButton
    private lateinit var btnEnter: MaterialButton

    // Contrato para solicitar permisos de cámara
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permiso concedido, iniciar el escaneo QR
                startQrScan()
            } else {
                // Permiso denegado
                Toast.makeText(this, "Permiso de cámara necesario para escanear QR", Toast.LENGTH_SHORT).show()
            }
        }

    // Contrato para recibir el resultado del escaneo QR
    private val scanQrResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                // Aquí manejas el resultado del escaneo
                // Puedes obtener los datos del código QR del intent
                // Por ejemplo, si usas una librería que devuelve el resultado en el intent
                // For ML Kit, you handle the result directly in the scanner's task listeners
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_class)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        inputLayout = findViewById(R.id.text_input_layout)
        etCode = findViewById(R.id.et_class_code)
        btnScanQR = findViewById(R.id.btn_scan_qr)
        btnEnter = findViewById(R.id.btn_enter)

        // 🔹 Verificación de rol (seguridad redundante)
        verificarRol { rol ->
            if (rol == "profesor") {
                startActivity(Intent(this, TeacherClassActivity::class.java))
                finish()
                return@verificarRol
            }

            // 🔸 Lógica para alumnos
            btnScanQR.setOnClickListener {
                // Verificar y solicitar permiso de cámara si es necesario
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Permiso ya concedido, iniciar el escaneo QR
                    startQrScan()
                } else {
                    // Solicitar permiso
                    requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
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

    private fun startQrScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                // Tarea completada con éxito, se encontró un código de barras
                val rawValue = barcode.rawValue
                // Aquí obtienes el valor del código QR (rawValue)
                // Puedes colocar este valor en el campo de texto del código de clase
                etCode.setText(rawValue)
                // O directamente validar y unirse a la clase
                // validarYUnirseAClase(rawValue.orEmpty())
            }
            .addOnCanceledListener {
                // Tarea cancelada por el usuario
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Tarea falló con una excepción
                Toast.makeText(this, "Error al escanear: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al unirse a la clase", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al validar clase", Toast.LENGTH_SHORT).show()
            }
    }
}
