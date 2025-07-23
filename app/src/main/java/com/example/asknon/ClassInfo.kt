package com.example.asknon

import com.google.firebase.firestore.DocumentId

data class ClassInfo(
    @DocumentId val id: String = "",
    val teacherId: String = "",
    val codigo: String = "",
    val createdAt: Long = 0L
)
