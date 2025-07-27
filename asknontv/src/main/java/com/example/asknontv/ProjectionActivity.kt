package com.example.asknontv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

// Definición de datos (asegúrate de que coincida con la del profesor)
data class Pregunta(
    val id: String = "",
    val texto: String = "",
    val respuesta: String? = null,
    val estado: String = "pendiente",
    val claseId: String = "",
    val estudianteId: String = "",
    val fechaCreacion: Date? = null // Puede ser null si no se inicializa
)

class ProjectionActivity : ComponentActivity() {

    private lateinit var db: FirebaseFirestore
    private var questionsListener: ListenerRegistration? = null
    private lateinit var currentClassId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        // Obtener classId del intent
        currentClassId = intent.getStringExtra("CLASS_ID") ?: ""

        if (currentClassId.isEmpty()) {
            Log.e("ProjectionActivity", "No se proporcionó CLASS_ID")
        } else {
            Log.d("ProjectionActivity", "CLASS_ID recibido: $currentClassId")
        }

        setContent {
            AskNonTVTheme {
                ProjectionScreen(classId = currentClassId, db = db)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        questionsListener?.remove()
        Log.d("ProjectionActivity", "Listener removido en onDestroy")
    }
}

@Composable
fun ProjectionScreen(classId: String, db: FirebaseFirestore) {
    // Estados para la UI
    var preguntaActual by remember { mutableStateOf<Pregunta?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // UI Principal - Mostrar estado inicial si no hay classId
    if (classId.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(scrollState)
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "❌ Error",
                color = Color.Red,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = "No se proporcionó ID de clase",
                color = Color.Gray,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
        }
        // Salimos temprano si no hay classId
        return
    }

    // Efecto para configurar el listener cuando el classId cambia y es válido
    DisposableEffect(key1 = classId) {
        Log.d("ProjectionScreen", "Iniciando listener para clase: $classId")

        // Limpiar estado anterior al iniciar el listener
        preguntaActual = null
        errorMessage = null
        isLoading = true // Se establece en true al iniciar

        // Configurar el listener de Firestore - Consulta simplificada
        // Evitamos ordenar por fechaCreacion para no necesitar un índice compuesto
        val listener: ListenerRegistration = db.collection("preguntas")
            .whereEqualTo("claseId", classId)
            .whereEqualTo("estado", "aprobada")
            // .orderBy("fechaCreacion", com.google.firebase.firestore.Query.Direction.DESCENDING) // Eliminado para evitar índice
            // .limit(1) // Eliminado para evitar índice
            .addSnapshotListener { snapshot, error ->
                // Siempre actualizamos el estado de carga al recibir datos por primera vez
                isLoading = false

                if (error != null) {
                    Log.e("ProjectionScreen", "Error de Firestore al cargar preguntas", error)
                    errorMessage = "Error al cargar preguntas: ${error.message}"
                    preguntaActual = null
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    try {
                        // Encontrar la pregunta más reciente manualmente
                        var preguntaMasReciente: Pregunta? = null
                        var fechaMasReciente: Date? = null

                        for (doc in snapshot.documents) {
                            val pregunta = doc.toObject(Pregunta::class.java)?.copy(id = doc.id)
                            if (pregunta != null) {
                                val fechaPregunta = pregunta.fechaCreacion
                                if (fechaPregunta != null) {
                                    if (fechaMasReciente == null || fechaPregunta.after(fechaMasReciente)) {
                                        fechaMasReciente = fechaPregunta
                                        preguntaMasReciente = pregunta
                                    }
                                } else if (preguntaMasReciente == null) {
                                    // Si no hay fecha, tomar la primera
                                    preguntaMasReciente = pregunta
                                }
                            }
                        }

                        if (preguntaMasReciente != null) {
                            Log.d("ProjectionScreen", "Pregunta más reciente cargada: ${preguntaMasReciente.texto}")
                            preguntaActual = preguntaMasReciente
                            errorMessage = null
                        } else {
                            Log.e("ProjectionScreen", "No se pudo determinar la pregunta más reciente")
                            preguntaActual = null
                            errorMessage = null
                        }
                    } catch (e: Exception) {
                        Log.e("ProjectionScreen", "Error al procesar snapshot", e)
                        errorMessage = "Error al procesar datos: ${e.message}"
                        preguntaActual = null
                    }
                } else {
                    Log.d("ProjectionScreen", "No hay preguntas aprobadas para esta clase ($classId)")
                    preguntaActual = null
                    errorMessage = null // No mostramos error, solo "sin preguntas"
                }
            }

        // Limpiar el listener cuando el composable se destruye o classId cambia
        onDispose {
            listener.remove()
            Log.d("ProjectionScreen", "Listener de Firestore removido en onDispose para clase: $classId")
        }
    }

    // UI Principal - Mostrar contenido basado en el estado
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            isLoading -> {
                Text(
                    text = "📺 Conectando...",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Text(
                    text = "Esperando preguntas aprobadas...",
                    color = Color.Gray,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
            errorMessage != null -> {
                Text(
                    text = "❌ Error",
                    color = Color.Red,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Text(
                    text = errorMessage!!,
                    color = Color.Gray,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
            preguntaActual != null -> {
                Text(
                    text = preguntaActual!!.texto,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                if (!preguntaActual!!.respuesta.isNullOrBlank()) {
                    Text(
                        text = "✅ ${preguntaActual!!.respuesta}",
                        color = Color.Green,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> {
                // Caso: no hay error, no está cargando, pero no hay pregunta
                Text(
                    text = "📭 No hay preguntas aprobadas",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Text(
                    text = "Las preguntas aprobadas por el profesor aparecerán aquí",
                    color = Color.Gray,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun AskNonTVTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}