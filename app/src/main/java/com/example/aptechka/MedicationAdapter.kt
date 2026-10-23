package com.example.aptechka

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat // ДОБАВЛЕНО для безопасного получения цвета из ресурсов
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicationAdapter(
    private var fullList: MutableList<Medication>
) : RecyclerView.Adapter<MedicationAdapter.ViewHolder>() {

    private var displayedList: MutableList<Medication> = fullList.toMutableList()

    val medications: List<Medication>
        get() = displayedList

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvTypeAndDosage: TextView = view.findViewById(R.id.tvTypeAndDosage)
        val tvExpiry: TextView = view.findViewById(R.id.tvExpiry)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val med = displayedList[position]
        holder.tvName.text = med.name
        holder.tvTypeAndDosage.text = "${med.type}, ${med.dosage}"

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val expiryString = dateFormat.format(Date(med.expiryDateMillis))
        val iconRes = when (med.type) {
            "Таблетки" -> R.drawable.tabl
            "Капсулы" -> R.drawable.caps
            "Сироп" -> R.drawable.sirop
            "Раствор" -> R.drawable.rastvor
            else -> R.drawable.quest
        }
        holder.ivIcon.setImageResource(iconRes)
        val currentTime = System.currentTimeMillis()
        val timeUntilExpiry = med.expiryDateMillis - currentTime
        val daysLeft = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(timeUntilExpiry)
        val brandBlueColor = ContextCompat.getColor(holder.itemView.context, R.color.empty_btn_text)

        when {
            timeUntilExpiry < 0 -> {
                holder.tvExpiry.setTextColor(Color.parseColor("#D32F2F"))
                holder.tvExpiry.text = "Просрочено! ($expiryString)"
                holder.ivIcon.setColorFilter(Color.parseColor("#D32F2F"))
            }

            daysLeft in 0L..7L -> {
                val warningOrange = Color.parseColor("#F57C00")
                holder.tvExpiry.setTextColor(warningOrange)
                holder.tvExpiry.text = if (daysLeft == 0L) {
                    "Истекает сегодня! ($expiryString)"
                } else {
                    "Срок: $expiryString ($daysLeft дн.)"
                }
                holder.ivIcon.setColorFilter(warningOrange)
            }

            else -> {
                holder.tvExpiry.setTextColor(brandBlueColor)
                holder.tvExpiry.text = "Годен до: $expiryString"
                holder.ivIcon.setColorFilter(brandBlueColor)
            }
        }
    }

    override fun getItemCount() = displayedList.size

    fun filter(query: String) {
        displayedList = if (query.isEmpty()) {
            fullList.toMutableList()
        } else {
            fullList.filter { it.name.contains(query, ignoreCase = true) }.toMutableList()
        }
        notifyDataSetChanged()
    }

    fun updateData(newData: List<Medication>) {
        fullList = newData.toMutableList()
        displayedList = fullList.toMutableList()
        notifyDataSetChanged()
    }

    fun removeItem(position: Int): Medication {
        val medToRemove = displayedList[position]
        fullList.remove(medToRemove)
        displayedList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, displayedList.size)
        return medToRemove
    }
}