package com.example.asknonwear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "asknon_notifications"
        private const val CHANNEL_NAME = "Notificaciones Asknon"
    }

    /**
     * Muestra una notificación interactiva en Wear OS.
     * @param role "alumno" o "profesor"
     * @param pregunta Pregunta que se mostrará en la notificación
     * @param respuesta Respuesta opcional (solo si aplica)
     */
    fun showNotification(role: String, pregunta: String, respuesta: String?) {
        createChannel()

        // 🔹 Intento que abre la GlanceActivity al tocar la notificación
        val intent = Intent(context, GlanceActivity::class.java).apply {
            putExtra("rol", role)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 🔸 Constructor de notificación
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_face) // Asegúrate de tener este ícono
            .setContentTitle("Asknon")
            .setContentText("📥 Nueva pregunta: \"$pregunta\"")
            .setColor(Color.BLUE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // 🔹 Acciones según rol
        if (role == "profesor") {
            builder.addAction(
                R.drawable.ic_check, // Ícono para acción
                "Marcar como leída",
                pendingIntent
            )
        } else {
            builder.addAction(
                R.drawable.ic_open_in_phone,
                "Ver en móvil",
                pendingIntent
            )
        }

        // 🔸 Mostrar notificación
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, builder.build())
    }

    /**
     * Crea el canal de notificación para Wear OS (solo una vez)
     */
    private fun createChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas de nuevas preguntas/respuestas"
            enableLights(true)
            lightColor = Color.GREEN
            enableVibration(true)
        }

        manager.createNotificationChannel(channel)
    }
}
