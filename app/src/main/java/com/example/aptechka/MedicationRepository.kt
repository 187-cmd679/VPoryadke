package com.example.aptechka
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class MedicationRepository(context: Context) {
    private val file = File(context.filesDir, "medications.json")
    private val gson = Gson()

    fun getMedications(): List<Medication> {
        if (!file.exists()) return emptyList()
        val json = file.readText()
        val type = object : TypeToken<List<Medication>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveMedications(medications: List<Medication>) {
        val json = gson.toJson(medications)
        file.writeText(json)
    }

    fun saveOrUpdateMedication(medication: Medication) {
        val current = getMedications().toMutableList()
        val index = current.indexOfFirst { it.id == medication.id }

        if (index != -1) {
            current[index] = medication
        } else {
            current.add(medication)
        }
        saveMedications(current)
    }

    fun removeMedication(id: String) {
        val current = getMedications().toMutableList()
        current.removeAll { it.id == id }
        saveMedications(current)
    }
}