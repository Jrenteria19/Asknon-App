package com.example.asknon

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class TeacherClassActivity : AppCompatActivity() {

    private lateinit var tvClassCode: TextView
    private lateinit var ivQrCode: ImageView // Added ImageView for QR code
    private lateinit var rvUnanswered: RecyclerView // Changed from pending
    private lateinit var rvAnswered: RecyclerView // Changed from approved
    private lateinit var btnProject: Button
    private lateinit var btnViewStudents: Button // Added button to view students
    private lateinit var btnDeleteClass: Button // Added button to delete class

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var classCode: String? = null
    private var claseId: String? = null
    private var teacherId: String? = null

    // Adaptadores para listas (ahora manejan FirestoreQuestion)
    private lateinit var unansweredAdapter: UnansweredQuestionAdapter // New adapter
    private lateinit var answeredAdapter: AnsweredQuestionAdapter // New adapter

    // Listeners para tiempo real
    private var unansweredQuestionsListener: ListenerRegistration? = null
    private var answeredQuestionsListener: ListenerRegistration? = null

    private val unansweredList = mutableListOf<FirestoreQuestion>()
    private val answeredList = mutableListOf<FirestoreQuestion>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_teacher)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        teacherId = auth.currentUser?.uid

        if (teacherId == null) {
            Toast.makeText(this, "Error: Usuario no autenticado.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Vincular vistas del layout
        tvClassCode = findViewById(R.id.tv_class_code)
        ivQrCode = findViewById(R.id.iv_qr_code) // Ensure you have this ImageView in your layout (e.g., in activity_class_teacher.xml)
        rvUnanswered = findViewById(R.id.rv_pending_questions) // Assuming you'll reuse this ID for unanswered questions
        rvAnswered = findViewById(R.id.rv_approved_questions) // Assuming you'll reuse this ID for answered questions
        btnProject = findViewById(R.id.btn_project_tv)
        btnViewStudents = findViewById(R.id.btn_view_students) // Ensure you have this Button in your layout
        btnDeleteClass = findViewById(R.id.btn_delete_class) // Ensure you have this Button in your layout


        // 🔹 Crear o cargar la clase del profesor
        createOrLoadTeacherClass(teacherId!!)

        // Configurar adaptadores (inicialmente vacíos)
        unansweredAdapter = UnansweredQuestionAdapter(unansweredList) { questionId ->
            // Acción al marcar como respondida
            markQuestionAsAnswered(questionId)
        }
        answeredAdapter = AnsweredQuestionAdapter(answeredList) // New adapter for answered questions

        // Configurar RecyclerViews
        rvUnanswered.layoutManager = LinearLayoutManager(this)
        rvUnanswered.adapter = unansweredAdapter

        rvAnswered.layoutManager = LinearLayoutManager(this)
        rvAnswered.adapter = answeredAdapter

        // Acción para botón de proyectar (adaptada a preguntas respondidas)
        btnProject.setOnClickListener {
            if (answeredList.isNotEmpty()) {
                // Project the most recent answered question (you might want a different logic)
                val questionToProject = answeredList.first()
                // TODO: Implement actual projection logic (e.g., send to TV app via a separate mechanism)
                Toast.makeText(this, "📺 Proyectando: \"${questionToProject.text}\"", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠️ No hay preguntas respondidas para proyectar", Toast.LENGTH_SHORT).show()
            }
        }

        // Acción para botón de ver alumnos
        btnViewStudents.setOnClickListener {
            if (claseId != null) {
                showStudentsDialog(claseId!!) // Call function to show students
            } else {
                Toast.makeText(this, "La clase aún no ha sido cargada.", Toast.LENGTH_SHORT).show()
            }
        }

        // Acción para botón de eliminar clase
        btnDeleteClass.setOnClickListener {
            showDeleteClassConfirmationDialog()
        }
    }

    // 🔹 Crea o carga la clase del profesor
    private fun createOrLoadTeacherClass(teacherId: String) {
        db.collection("clases")
            .whereEqualTo("teacherId", teacherId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    // 🔸 No existe clase para este profesor, crear una nueva
                    generateUniqueClassCode { code ->
                        val newClass = hashMapOf(
                            "teacherId" to teacherId,
                            "codigo" to code,
                            "createdAt" to System.currentTimeMillis()
                            // Add other class properties if needed
                        )
                        db.collection("clases").add(newClass)
                            .addOnSuccessListener { documentReference ->
                                claseId = documentReference.id
                                classCode = code
                                displayClassInfo(code, claseId)
                                startQuestionListeners(claseId!!)
                                Toast.makeText(this, "Clase creada con código: $code", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                Log.e("TeacherClassActivity", "Error creating class", e)
                                Toast.makeText(this, "Error al crear la clase.", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    // 🔸 Ya existe una clase para este profesor, cargarla
                    val classDocument = querySnapshot.documents.first()
                    claseId = classDocument.id
                    classCode = classDocument.getString("codigo")
                    displayClassInfo(classCode, claseId)
                    startQuestionListeners(claseId!!)
                    Toast.makeText(this, "Clase cargada con código: $classCode", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("TeacherClassActivity", "Error loading class", e)
                Toast.makeText(this, "Error al cargar la clase.", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 Genera un código numérico único para la clase y verifica unicidad en Firestore
    private fun generateUniqueClassCode(onCodeGenerated: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            var unique = false
            var code = ""
            while (!unique) {
                code = Random.nextInt(100000, 999999).toString() // 6-digit numeric code
                try {
                    val snapshot = db.collection("clases")
                        .whereEqualTo("codigo", code)
                        .get()
                        .await()
                    unique = snapshot.isEmpty
                } catch (e: Exception) {
                    Log.e("TeacherClassActivity", "Error checking code uniqueness", e)
                    // Handle error, potentially break loop or retry
                    break // Example: break on error
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                if (unique) {
                    onCodeGenerated(code)
                } else {
                    Toast.makeText(this@TeacherClassActivity, "Error al generar código único.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // 🔹 Muestra la información de la clase (código y QR)
    private fun displayClassInfo(code: String?, claseId: String?) {
        if (code != null && claseId != null) {
            tvClassCode.text = "Código de Clase: $code"
            // Generar el enlace para el QR (ejemplo: applink://joinclass?code=XYZ&id=ABC)
            // Debes definir un Scheme y Host para tu app en AndroidManifest para Deep Linking
            val deepLink = "asknonapp://joinclass?code=$code&id=$claseId"
            generateQrCode(deepLink)
        } else {
            tvClassCode.text = "Código: Generando..."
            ivQrCode.setImageDrawable(null) // Clear previous QR
        }
    }

    // 🔹 Genera y muestra el código QR
    private fun generateQrCode(text: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 400, 400)
            ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("TeacherClassActivity", "Error generating QR code", e)
            Toast.makeText(this, "Error al generar código QR.", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔹 Inicia los listeners para escuchar preguntas en tiempo real
    private fun startQuestionListeners(claseId: String) {
        // Listener para preguntas no respondidas
        unansweredQuestionsListener = db.collection("questions")
            .whereEqualTo("claseId", claseId)
            .whereEqualTo("isAnswered", false)
            .orderBy("timestamp") // Order by time
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("TeacherClassActivity", "Listen for unanswered failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    unansweredList.clear()
                    for (doc in snapshots) {
                        val question = doc.toObject(FirestoreQuestion::class.java).copy(id = doc.id)
                        unansweredList.add(question)
                    }
                    unansweredAdapter.notifyDataSetChanged()
                }
            }

        // Listener para preguntas respondidas
        answeredQuestionsListener = db.collection("questions")
            .whereEqualTo("claseId", claseId)
            .whereEqualTo("isAnswered", true)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING) // Mostrar respondidas más recientes primero
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("TeacherClassActivity", "Listen for answered failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    answeredList.clear()
                    for (doc in snapshots) {
                        val question = doc.toObject(FirestoreQuestion::class.java).copy(id = doc.id)
                        answeredList.add(question)
                    }
                    answeredAdapter.notifyDataSetChanged()
                }
            }
    }

    // 🔹 Marca una pregunta como respondida en Firestore
    private fun markQuestionAsAnswered(questionId: String) {
        db.collection("questions").document(questionId)
            .update("isAnswered", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Pregunta marcada como respondida.", Toast.LENGTH_SHORT).show()
                // No necesitas eliminar, el listener de answered Questions la moverá automáticamente
            }
            .addOnFailureListener { e ->
                Log.e("TeacherClassActivity", "Error marking question as answered", e)
                Toast.makeText(this, "Error al marcar pregunta.", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 Implementa la lógica para mostrar la lista de alumnos
    private fun showStudentsDialog(claseId: String) {
        // Fetch students for this class from Firestore
        db.collection("users")
            .whereEqualTo("claseId", claseId)
            .whereEqualTo("rol", "alumno") // Ensure we only get students
            .get()
            .addOnSuccessListener { querySnapshot ->
                val students = querySnapshot.documents.mapNotNull { doc ->
                    // Map Firestore document to StudentInfo data class
                    doc.toObject(StudentInfo::class.java)?.copy(id = doc.id)
                }
                // TODO: Implement a custom AlertDialog or launch a new Activity
                // to display the 'students' list.
                // The list should show student ID (or a name if you add one) and questionCount.
                // Each item should have an option to remove the student.

                // Example: Displaying in a simple Toast for now
                val studentListText = students.joinToString("\n") { "ID: ${it.id}, Preguntas: ${it.questionCount}" }
                AlertDialog.Builder(this)
                    .setTitle("Alumnos en la Clase")
                    .setMessage(if (students.isEmpty()) "No hay alumnos en esta clase." else studentListText)
                    .setPositiveButton("OK", null)
                    .show()

                // Inside your custom dialog/activity, you would add listeners for removing students:
                // fun removeStudent(studentId: String) {
                //     db.collection("users").document(studentId)
                //         .update("claseId", null) // Or remove the field
                //         .addOnSuccessListener {
                //             Toast.makeText(this, "Alumno eliminado.", Toast.LENGTH_SHORT).show()
                //             // The listener for students in the dialog/activity would update the list
                //         }
                //         .addOnFailureListener { e ->
                //             Log.e("TeacherClassActivity", "Error removing student", e)
                //             Toast.makeText(this, "Error al eliminar alumno.", Toast.LENGTH_SHORT).show()
                //         }
                // }

            }
            .addOnFailureListener { e ->
                Log.e("TeacherClassActivity", "Error fetching students", e)
                Toast.makeText(this, "Error al cargar la lista de alumnos.", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 Muestra un diálogo de confirmación para eliminar la clase
    private fun showDeleteClassConfirmationDialog() {
        if (claseId == null) {
            Toast.makeText(this, "La clase aún no ha sido cargada.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Eliminar Clase")
            .setMessage("¿Estás seguro de que quieres eliminar esta clase permanentemente? Esto eliminará todas las preguntas y desvinculará a los alumnos.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteClass(claseId!!)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // 🔹 Elimina la clase y datos asociados de Firestore
    private fun deleteClass(claseId: String) {
        // Note: Deleting a document in Firestore does NOT automatically delete its subcollections.
        // If you had subcollections under the class document (e.g., 'clases/{claseId}/questions'),
        // you would need to explicitly delete those subcollections as well.
        // In this structure, 'questions' is a top-level collection, so we delete questions by claseId.

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Delete questions associated with the class
                val questionsSnapshot = db.collection("questions")
                    .whereEqualTo("claseId", claseId)
                    .get()
                    .await()

                for (doc in questionsSnapshot.documents) {
                    doc.reference.delete().await()
                }

                // 2. Unlink students from this class
                val studentsSnapshot = db.collection("users")
                    .whereEqualTo("claseId", claseId)
                    .get()
                    .await()

                for (doc in studentsSnapshot.documents) {
                    doc.reference.update("claseId", null).await() // Or remove the field
                }

                // 3. Delete the class document itself
                db.collection("clases").document(claseId).delete().await()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@TeacherClassActivity, "Clase eliminada correctamente.", Toast.LENGTH_SHORT).show()
                    finish() // Close the activity after deletion
                }

            } catch (e: Exception) {
                Log.e("TeacherClassActivity", "Error deleting class", e)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@TeacherClassActivity, "Error al eliminar la clase.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // 🔹 Detener los listeners para evitar fugas de memoria
        unansweredQuestionsListener?.remove()
        answeredQuestionsListener?.remove()
    }

    // 🔹 Método dummy para mostrar alumnos (debes implementar la UI real)
    // Consulta la base de datos y muestra los alumnos en un formato simple por ahora.
    // Deberías reemplazar esto con un AlertDialog o Activity personalizado.
    /*
    private fun showStudentsDialog(claseId: String) {
        // Esta función ya está implementada arriba dentro del listener del botón btnViewStudents
        // La dejo comentada aquí para referencia.
    }
    */
}
