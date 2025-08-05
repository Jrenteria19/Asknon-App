// asknontv/src/main/java/com/example/asknontv/ProjectionActivity.kt

package com.example.asknontv

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
    val estado: String = "pendiente", // "pendiente", "aprobada", "rechazada"
    val claseId: String = "",
    val estudianteId: String = "",
    val fechaCreacion: Date? = null // Puede ser null si no se inicializa
)

class ProjectionActivity : ComponentActivity() {
    private lateinit var db: FirebaseFirestore
    private var questionsListener: ListenerRegistration? = null
    private var currentClassId: String = ""

    companion object {
        private const val TAG = "ProjectionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        // Obtener classId del intent
        val classIdFromIntent = intent.getStringExtra("CLASS_ID")

        if (!classIdFromIntent.isNullOrEmpty()) {
            currentClassId = classIdFromIntent
            Log.d(TAG, "CLASS_ID recibido automáticamente: $currentClassId")
            // Iniciar directamente con la pantalla de proyección
            setContent {
                AskNonTVTheme {
                    ProjectionScreen(classId = currentClassId, db = db)
                }
            }
        } else {
            Log.d(TAG, "No se proporcionó CLASS_ID, mostrando pantalla de ingreso manual")
            // Mostrar pantalla para ingresar el código de clase
            setContent {
                AskNonTVTheme {
                    ClassCodeInputScreen(onClassCodeEntered = { classCode ->
                        if (classCode.isNotBlank()) {
                            currentClassId = classCode
                            // Cambiar a la pantalla de proyección
                            setContent {
                                AskNonTVTheme {
                                    ProjectionScreen(classId = currentClassId, db = db)
                                }
                            }
                        } else {
                            Toast.makeText(this, "Por favor, ingresa un código de clase", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        questionsListener?.remove()
        Log.d(TAG, "Listener removido en onDestroy")
    }
}

// --- Pantalla de Ingreso de Código (Versión Corregida) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassCodeInputScreen(onClassCodeEntered: (String) -> Unit) {
    var classCode by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    // Obtiene el FocusManager para poder limpiar el foco (y potencialmente cerrar el teclado)
    val focusManager = LocalFocusManager.current
    // Obtiene el contexto para mostrar el Toast
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📺 Conexión a Clase",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Text(
            text = "Ingresa el código de clase para conectarte",
            color = Color.Gray,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // --- OutlinedTextField Corregido ---
        OutlinedTextField(
            value = classCode,
            onValueChange = { classCode = it },
            label = { Text("Código de Clase") },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = Color.White,
                fontSize = 20.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            // Configura el teclado para tener una acción "Done"
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done // Muestra un botón "Done" en el teclado
            ),
            // Define qué hacer cuando se presiona "Done"
            keyboardActions = KeyboardActions(
                onDone = {
                    // Intenta enviar el código
                    if (classCode.isNotBlank()) {
                        // Cierra el teclado al limpiar el foco
                        focusManager.clearFocus()
                        onClassCodeEntered(classCode)
                    } else {
                        // Mostrar un mensaje si está vacío usando el contexto local
                        Toast.makeText(context, "Por favor, ingresa un código de clase", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        )
        // --- Fin OutlinedTextField ---

        Button(
            onClick = {
                // También permite enviar el código presionando el botón
                if (classCode.isNotBlank()) {
                    // Cierra el teclado al limpiar el foco también aquí
                    focusManager.clearFocus()
                    onClassCodeEntered(classCode)
                } else {
                    // Mostrar un mensaje si está vacío usando el contexto local
                    Toast.makeText(context, "Por favor, ingresa un código de clase", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Conectar",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// --- Pantalla de Proyección (Versión Corregida) ---
@Composable
fun ProjectionScreen(classId: String, db: FirebaseFirestore) {
    // Estados para la UI
    var preguntaActual by remember { mutableStateOf<Pregunta?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var questionsCount by remember { mutableStateOf(0) } // Para debugging
    val scrollState = rememberScrollState()

    // Mostrar el ID de clase en la parte superior para referencia
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Clase ID: $classId",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.End)
        )

        // Contenido principal
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
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
                        text = "Las preguntas aprobadas por el profesor aparecerán aquí\n(Total: $questionsCount)",
                        color = Color.Gray,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Efecto para configurar el listener cuando el classId cambia y es válido
    DisposableEffect(key1 = classId) {
        Log.d("ProjectionScreen", "Iniciando listener para clase: $classId")
        // Limpiar estado anterior al iniciar el listener
        preguntaActual = null
        errorMessage = null
        isLoading = true // Se establece en true al iniciar
        questionsCount = 0

        // Configurar el listener de Firestore - Consulta corregida
        val listener: ListenerRegistration = db.collection("preguntas")
            .whereEqualTo("claseId", classId)
            .whereEqualTo("estado", "aprobada")
            .addSnapshotListener { snapshot, error ->
                // Siempre actualizamos el estado de carga al recibir datos por primera vez
                isLoading = false
                if (error != null) {
                    Log.e("ProjectionScreen", "Error de Firestore al cargar preguntas", error)
                    errorMessage = "Error al cargar preguntas: ${error.message}"
                    preguntaActual = null
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.d("ProjectionScreen", "Snapshot recibido. Documentos: ${snapshot.size()}, Vacío: ${snapshot.isEmpty}")
                    questionsCount = snapshot.size()

                    if (!snapshot.isEmpty) {
                        try {
                            val preguntasAprobadas = mutableListOf<Pregunta>()

                            // Procesar todos los documentos
                            for (doc in snapshot.documents) {
                                Log.d("ProjectionScreen", "Procesando documento ID: ${doc.id}")
                                Log.d("ProjectionScreen", "Datos del documento: ${doc.data}")

                                val pregunta = doc.toObject(Pregunta::class.java)?.copy(id = doc.id)
                                if (pregunta != null) {
                                    Log.d("ProjectionScreen", "Pregunta convertida: ${pregunta.texto}, Estado: ${pregunta.estado}, ClaseId: ${pregunta.claseId}")

                                    // Verificar que la pregunta sea de la clase correcta y esté aprobada
                                    if (pregunta.claseId == classId && pregunta.estado == "aprobada") {
                                        preguntasAprobadas.add(pregunta)
                                        Log.d("ProjectionScreen", "Pregunta añadida a la lista: ${pregunta.texto}")
                                    } else {
                                        Log.d("ProjectionScreen", "Pregunta filtrada - ClaseId no coincide o estado no es aprobada")
                                    }
                                } else {
                                    Log.w("ProjectionScreen", "No se pudo convertir el documento a Pregunta: ${doc.id}")
                                }
                            }

                            Log.d("ProjectionScreen", "Total preguntas aprobadas válidas: ${preguntasAprobadas.size}")

                            if (preguntasAprobadas.isNotEmpty()) {
                                // Encontrar la pregunta más reciente
                                val preguntaMasReciente = preguntasAprobadas.maxByOrNull {
                                    it.fechaCreacion ?: Date(0)
                                } ?: preguntasAprobadas.firstOrNull()

                                if (preguntaMasReciente != null) {
                                    Log.d("ProjectionScreen", "Pregunta más reciente seleccionada: ${preguntaMasReciente.texto}")
                                    preguntaActual = preguntaMasReciente
                                    errorMessage = null
                                } else {
                                    Log.e("ProjectionScreen", "No se pudo determinar la pregunta más reciente")
                                    preguntaActual = null
                                    errorMessage = null
                                }
                            } else {
                                Log.d("ProjectionScreen", "No hay preguntas aprobadas válidas para esta clase ($classId)")
                                preguntaActual = null
                                errorMessage = null
                            }
                        } catch (e: Exception) {
                            Log.e("ProjectionScreen", "Error al procesar snapshot", e)
                            errorMessage = "Error al procesar datos: ${e.message}"
                            preguntaActual = null
                        }
                    } else {
                        Log.d("ProjectionScreen", "No hay documentos en el snapshot para esta clase ($classId)")
                        preguntaActual = null
                        errorMessage = null
                    }
                } else {
                    Log.d("ProjectionScreen", "Snapshot es null para esta clase ($classId)")
                    preguntaActual = null
                    errorMessage = null
                }
            }

        // Limpiar el listener cuando el composable se destruye o classId cambia
        onDispose {
            listener.remove()
            Log.d("ProjectionScreen", "Listener de Firestore removido en onDispose para clase: $classId")
        }
    }
}

// --- Tema Básico ---
@Composable
fun AskNonTVTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}