package com.example.asknon

import com.google.firebase.firestore.DocumentId

data class FirestoreQuestion(
    @DocumentId val id: String = "", // Campo para almacenar el ID del documento
    val text: String = "",
    val userId: String = "",
    val claseId: String = "",
    val timestamp: Long = 0L,
    val isAnswered: Boolean = false,
    val answer: String? = null // Mantenemos esto por si se quisiera añadir respuesta textual después
)
