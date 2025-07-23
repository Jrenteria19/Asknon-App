package com.example.asknon

import com.google.firebase.firestore.DocumentId

data class StudentInfo(
    @DocumentId val id: String = "", // User ID
    val rol: String = "", // Should be "alumno"
    val claseId: String? = null, // The class they are in
    val questionCount: Int = 0 // Added for scoring
    // Add other student fields if you have them
)
