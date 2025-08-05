package com.example.asknon

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DataLayerListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "PhoneDataLayerService"
        private const val APPROVE_ALL_PATH = "/approve_all_questions"
    }

    private val db = FirebaseFirestore.getInstance()

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == APPROVE_ALL_PATH) {
            Log.d(TAG, "Orden de aprobar todas las preguntas recibida del reloj.")
            approveAllPendingQuestionsInFirestore()
        }
    }

    // Esta es la misma lógica de tu TeacherClassActivity, pero ahora se
    // puede ejecutar desde cualquier parte, incluso si la actividad no está abierta.
    private fun approveAllPendingQuestionsInFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("clases")
            .whereEqualTo("profesorId", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { classSnapshot ->
                if (classSnapshot.isEmpty) return@addOnSuccessListener
                val classId = classSnapshot.documents[0].id

                db.collection("preguntas")
                    .whereEqualTo("claseId", classId)
                    .whereEqualTo("estado", "pendiente")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.isEmpty) return@addOnSuccessListener

                        val batch = db.batch()
                        snapshot.documents.forEach { doc ->
                            batch.update(doc.reference, "estado", "aprobada")
                        }

                        batch.commit()
                            .addOnSuccessListener { Log.d(TAG, "Preguntas aprobadas desde el servicio.") }
                            .addOnFailureListener { e -> Log.e(TAG, "Error al hacer commit del batch", e) }
                    }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error al obtener la clase del profesor", e) }
    }
}