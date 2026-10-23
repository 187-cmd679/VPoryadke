package com.example.aptechka

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmScheduler(private val context: Context) {

    companion object {
        private const val SEVEN_DAYS_IN_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    fun scheduleAlarm(medication: Medication) {
        val reminderTime = medication.reminderTimeMillis ?: return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MED_NAME", medication.name)
            putExtra("MED_DOSAGE", medication.dosage)
            putExtra("IS_EXPIRY", false)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminderTime,
                pendingIntent
            )
        }
    }

    fun scheduleExpiryAlarm(medication: Medication) {
        if (medication.expiryDateMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MED_NAME", medication.name)
            putExtra("IS_EXPIRY", true)
            putExtra("IS_EARLY_WARNING", false) // Это финальный день
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.hashCode() + 50000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    medication.expiryDateMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    medication.expiryDateMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                medication.expiryDateMillis,
                pendingIntent
            )
        }
    }

    fun scheduleEarlyExpiryAlarm(medication: Medication) {
        val triggerTime = medication.expiryDateMillis - SEVEN_DAYS_IN_MILLIS
        if (triggerTime <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MED_NAME", medication.name)
            putExtra("IS_EXPIRY", true)
            putExtra("IS_EARLY_WARNING", true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.hashCode() + 100000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelReminderAlarm(medication: Medication) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pIntentReminder = PendingIntent.getBroadcast(
            context, medication.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pIntentReminder)
        pIntentReminder.cancel()
    }

    fun cancelAlarm(medication: Medication) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        cancelReminderAlarm(medication)

        val pIntentExpiry = PendingIntent.getBroadcast(
            context, medication.id.hashCode() + 50000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pIntentExpiry)
        pIntentExpiry.cancel()

        val pIntentEarlyExpiry = PendingIntent.getBroadcast(
            context, medication.id.hashCode() + 100000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pIntentEarlyExpiry)
        pIntentEarlyExpiry.cancel()
    }
}