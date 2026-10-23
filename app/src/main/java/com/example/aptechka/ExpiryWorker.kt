package com.example.aptechka
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ExpiryWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val repo = MedicationRepository(applicationContext)
        val medications = repo.getMedications()
        val currentTime = System.currentTimeMillis()
        val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
        for (med in medications) {
            val timeUntilExpiry = med.expiryDateMillis - currentTime

            if (timeUntilExpiry < 0) {
                showNotification("Срок годности истек!", "Лекарство ${med.name} просрочено.", med.id.hashCode())
            } else if (timeUntilExpiry <= thirtyDaysMillis) {
                showNotification("Скоро истечет срок!", "У ${med.name} заканчивается срок годности.", med.id.hashCode() + 1)
            }
        }
        return Result.success()
    }

    private fun showNotification(title: String, text: String, notificationId: Int) {
        val notification = NotificationCompat.Builder(applicationContext, "MED_CHANNEL_V2")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}