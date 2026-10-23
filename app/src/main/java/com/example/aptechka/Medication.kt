package com.example.aptechka
import java.util.UUID

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String,
    val dosage: String,
    val expiryDateMillis: Long,
    var reminderTimeMillis: Long? = null,
    var isNotificationEnabled: Boolean = true
)