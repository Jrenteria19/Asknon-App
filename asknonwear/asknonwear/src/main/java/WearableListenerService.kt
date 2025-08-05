package com.example.asknonwear

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class WearMessageListenerService : WearableListenerService() {

    companion object {
        private const val PENDING_QUESTIONS_PATH = "/pending_questions_count"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == PENDING_QUESTIONS_PATH) {
            val count = String(messageEvent.data, StandardCharsets.UTF_8).toIntOrNull() ?: 0

            // Envía el conteo a la Activity principal
            val intent = Intent(this, MainActivityWear::class.java).apply {
                putExtra("pending_count", count)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }
}