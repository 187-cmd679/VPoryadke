package com.example.aptechka

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medName = intent.getStringExtra("MED_NAME") ?: "Лекарство"
        val dosage = intent.getStringExtra("MED_DOSAGE") ?: ""

        val isExpiry = intent.getBooleanExtra("IS_EXPIRY", false)

        val notification = if (isExpiry) {
            val isEarlyWarning = intent.getBooleanExtra("IS_EARLY_WARNING", false)
            val title = if (isEarlyWarning) "Срок годности истекает!" else "Срок годности истек!"
            val text = if (isEarlyWarning) {
                "Лекарство $medName испортится через 7 дней! Рекомендуем обновить запасы."
            } else {
                "Лекарство $medName просрочено. Использовать нельзя!"
            }
            NotificationCompat.Builder(context, "MED_CHANNEL_V2")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        } else {
            NotificationCompat.Builder(context, "MED_CHANNEL_V2")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Время принять лекарство!")
                .setContentText("$medName — $dosage")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isEarlyWarning = intent.getBooleanExtra("IS_EARLY_WARNING", false)
        val notificationId = when {
            isEarlyWarning -> medName.hashCode() + 2000
            isExpiry -> medName.hashCode() + 1000
            else -> medName.hashCode()
        }
        manager.notify(notificationId, notification)
    }
}