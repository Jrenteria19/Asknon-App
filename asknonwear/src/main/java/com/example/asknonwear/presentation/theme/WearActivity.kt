package com.example.asknonwear

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import com.example.asknonwear.theme.WearMaterialTheme

// Definición de datos, idéntica a la de la TV
data class Pregunta(
    val id: String = "",
    val texto: String = "",
    val respuesta: String? = null,
    val estado: String = "pendiente",
    val claseId: String = "",
    val estudianteId: String = "",
    val fechaCreacion: Date? = null
)

class WearActivity : ComponentActivity() {

    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase en la app del reloj
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        setContent {
            WearMaterialTheme {
                var classId by remember { mutableStateOf<String?>(null) }

                if (classId == null) {
                    // Pantalla 1: Ingresar código de clase
                    ClassCodeInputScreen(onCodeEntered = { enteredCode ->
                        if (enteredCode.isNotBlank()) {
                            classId = enteredCode
                        }
                    })
                } else {
                    // Pantalla 2: Ver contador y aprobar
                    PendingQuestionsScreen(classId = classId!!, db = db)
                }
            }
        }
    }
}

// --- PANTALLA PARA INGRESAR EL CÓDIGO DE CLASE (VERSIÓN WEAR OS) ---
@Composable
fun ClassCodeInputScreen(onCodeEntered: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text(
                text = "Conectar a Clase",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        item {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Código") },
                modifier = Modifier.fillMaxWidth(0.8f).padding(top = 8.dp)
            )
        }
        item {
            Button(
                onClick = { onCodeEntered(text) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Conectar")
            }
        }
    }
}

// --- PANTALLA PARA VER PREGUNTAS PENDIENTES Y APROBAR ---
@Composable
fun PendingQuestionsScreen(classId: String, db: FirebaseFirestore) {
    var pendingCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    DisposableEffect(key1 = classId) {
        val listener = db.collection("preguntas")
            .whereEqualTo("claseId", classId)
            .whereEqualTo("estado", "pendiente") // <-- La consulta clave es por "pendiente"
            .addSnapshotListener { snapshot, error ->
                isLoading = false
                if (error != null) {
                    Log.e("PendingScreen", "Error de Firestore", error)
                    return@addSnapshotListener
                }
                pendingCount = snapshot?.size() ?: 0
                Log.d("PendingScreen", "Preguntas pendientes: $pendingCount")
            }
        onDispose { listener.remove() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pendientes", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isLoading) "..." else pendingCount.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 50.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    approveAllPendingQuestions(classId, db, context)
                }
            },
            enabled = pendingCount > 0 && !isLoading,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF32CD32)) // Verde
        ) {
            Text("Aprobar Todas")
        }
    }
}

// --- LÓGICA PARA APROBAR TODAS LAS PREGuntas ---
private suspend fun approveAllPendingQuestions(classId: String, db: FirebaseFirestore, context: Context) {
    try {
        val querySnapshot = db.collection("preguntas")
            .whereEqualTo("claseId", classId)
            .whereEqualTo("estado", "pendiente")
            .get()
            .await()

        if (querySnapshot.isEmpty) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No hay nada que aprobar", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val batch = db.batch()
        querySnapshot.documents.forEach { doc ->
            batch.update(doc.reference, "estado", "aprobada")
        }

        batch.commit().await()

        Log.d("approveAll", "Lote de ${querySnapshot.size()} aprobaciones completado.")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "${querySnapshot.size()} aprobadas!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Log.e("approveAll", "Error al aprobar todas las preguntas", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Error al aprobar", Toast.LENGTH_SHORT).show()
        }
    }
}
