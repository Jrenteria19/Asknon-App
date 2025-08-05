package com.example.asknonwear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.nio.charset.StandardCharsets

class YourMessageListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearMessageListener"
        private const val PENDING_QUESTIONS_PATH = "/pending_questions_count"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Mensaje recibido: ${messageEvent.path}")
        if (messageEvent.path == PENDING_QUESTIONS_PATH) {
            val message = String(messageEvent.data, StandardCharsets.UTF_8)
            Log.d(TAG, "Conteo de preguntas pendientes: $message")
            // Aquí actualizas la UI de tu app Wear OS con el conteo (e.g., usando un ViewModel, LiveData, Broadcast, etc.)
            // Por ejemplo, podrías enviar un LocalBroadcast para que tu Activity lo reciba:
            // val intent = Intent("PENDING_QUESTIONS_UPDATE")
            // intent.putExtra("count", message.toIntOrNull() ?: 0)
            // LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
        