package com.example.aptechka

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var repository: MedicationRepository
    private lateinit var alarmScheduler: AlarmScheduler

    private lateinit var adapter: MedicationAdapter
    private lateinit var recyclerView: RecyclerView

    private lateinit var emptyView: LinearLayout

    private var activeEtName: EditText? = null
    private var activeEtDosage: EditText? = null
    private var activeEtType: EditText? = null

    private lateinit var docxPickerLauncher: ActivityResultLauncher<String>

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Напоминания успешно включены!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Без разрешений напоминания приходить не будут!", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        repository = MedicationRepository(this)
        alarmScheduler = AlarmScheduler(this)

        emptyView = findViewById(R.id.emptyView)
        val btnEmptyAdd = findViewById<Button>(R.id.btnEmptyAdd)
        btnEmptyAdd.setOnClickListener { showAddMedicationDialog() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MedicationAdapter(repository.getMedications().toMutableList())
        recyclerView.adapter = adapter
        updateEmptyState()
        recyclerView.itemAnimator = DefaultItemAnimator()

        recyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            val gestureDetector = GestureDetector(this@MainActivity,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent) {
                        val child = recyclerView.findChildViewUnder(e.x, e.y)
                        if (child != null) {
                            val position = recyclerView.getChildAdapterPosition(child)
                            if (position != RecyclerView.NO_POSITION && position < adapter.medications.size) {
                                val selectedMedication = adapter.medications[position]
                                window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                showMedicationMenu(selectedMedication)
                            }
                        }
                    }
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        val child = recyclerView.findChildViewUnder(e.x, e.y)
                        if (child != null) {
                            val position = recyclerView.getChildAdapterPosition(child)
                            if (position != RecyclerView.NO_POSITION && position < adapter.medications.size) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Удерживайте карточку для настройки",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        return true
                    }
                })

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(e)
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText?.trim().orEmpty())
                return true
            }
        })

        val topBar = findViewById<LinearLayout>(R.id.topBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val extraPadding = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, insets.top + extraPadding, view.paddingRight, view.paddingBottom)
            windowInsets
        }

        if (savedInstanceState == null) {
            createNotificationChannel()
            setupExpiryWorker()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        docxPickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    processDocxFile(uri)
                } else {
                    Toast.makeText(this, "Файл не выбран", Toast.LENGTH_SHORT).show()
                }
            }

        val btnAddTop = findViewById<ImageButton>(R.id.btnAddTop)
        val btnSort = findViewById<ImageButton>(R.id.btnSort)

        btnAddTop.setOnClickListener { showAddMedicationDialog() }
        btnSort.setOnClickListener { showSortDialog() }

        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.5f

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) {
                    adapter.notifyItemChanged(position)
                    return
                }

                val medToRemove = adapter.removeItem(position)
                alarmScheduler.cancelAlarm(medToRemove)
                repository.removeMedication(medToRemove.id)

                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
                updateEmptyState()
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
                val itemView = vh.itemView
                val cornerRadius = 16 * itemView.context.resources.displayMetrics.density
                val path = Path()
                path.addRoundRect(RectF(itemView.left.toFloat(), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat()), cornerRadius, cornerRadius, Path.Direction.CW)

                c.save()
                c.clipPath(path)
                val background = ColorDrawable(Color.parseColor("#D32F2F"))
                background.setBounds((itemView.right + dX).toInt(), itemView.top, itemView.right, itemView.bottom)
                background.draw(c)
                c.restore()

                val icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_delete)
                icon?.let {
                    val iconMargin = 32
                    val iconSize = it.intrinsicHeight
                    val iconTop = itemView.top + (itemView.height - iconSize) / 2
                    val iconLeft = itemView.right - iconMargin - iconSize
                    val iconRight = itemView.right - iconMargin
                    it.setBounds(iconLeft, iconTop, iconRight, iconTop + iconSize)
                    if (dX < -iconMargin * 2) it.draw(c)
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        }

        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(recyclerView)
    }

    private fun checkExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
                Toast.makeText(this, "Предоставьте разрешение на точные напоминания", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    private fun showMedicationMenu(med: Medication) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_reminders, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvInfo = dialogView.findViewById<android.widget.TextView>(R.id.tvMedicationInfo)
        tvInfo.text = "Препарат: ${med.name} (${med.type})"

        val btnDisableReminders = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDisableReminders)
        val btnDeleteMedication = dialogView.findViewById<android.view.View>(R.id.btnDeleteMedication)

        if (med.reminderTimeMillis == null) {
            btnDisableReminders.text = "Добавить напоминание"
        } else {
            btnDisableReminders.text = if (med.isNotificationEnabled) "Отключить напоминания" else "Включить напоминания"
        }

        btnDisableReminders.setOnClickListener {
            btnDisableReminders.isEnabled = false

            if (med.reminderTimeMillis == null) {
                val calendar = Calendar.getInstance()
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                    .setMinute(calendar.get(Calendar.MINUTE))
                    .setTitleText("Выберите время напоминания")
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    val reminderCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, timePicker.hour)
                        set(Calendar.MINUTE, timePicker.minute)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                    }

                    if (!checkExactAlarmPermission()) {
                        btnDisableReminders.isEnabled = true
                        return@addOnPositiveButtonClickListener
                    }

                    med.reminderTimeMillis = reminderCalendar.timeInMillis
                    med.isNotificationEnabled = true

                    alarmScheduler.scheduleAlarm(med)
                    repository.saveOrUpdateMedication(med)
                    adapter.updateData(repository.getMedications())

                    Toast.makeText(this, "Напоминание для ${med.name} успешно добавлено!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                timePicker.addOnDismissListener {
                    btnDisableReminders.isEnabled = true
                }

                timePicker.show(supportFragmentManager, "time_picker_later")

            } else {
                if (!med.isNotificationEnabled && !checkExactAlarmPermission()) {
                    btnDisableReminders.isEnabled = true
                    return@setOnClickListener
                }

                med.isNotificationEnabled = !med.isNotificationEnabled
                if (med.isNotificationEnabled) {
                    alarmScheduler.scheduleAlarm(med)
                    Toast.makeText(this, "Напоминания о приеме включены", Toast.LENGTH_SHORT).show()
                } else {
                    alarmScheduler.cancelReminderAlarm(med)
                    Toast.makeText(this, "Напоминания о приеме выключены", Toast.LENGTH_SHORT).show()
                }
                repository.saveOrUpdateMedication(med)
                adapter.updateData(repository.getMedications())
                dialog.dismiss()
            }
        }

        btnDeleteMedication.setOnClickListener {
            btnDeleteMedication.isEnabled = false
            val currentPos = adapter.medications.indexOfFirst { it.id == med.id }
            if (currentPos != -1) {
                val removed = adapter.removeItem(currentPos)
                alarmScheduler.cancelAlarm(removed)
                repository.removeMedication(removed.id)
                updateEmptyState()
                Toast.makeText(this, "${med.name} удалено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ошибка: препарат не найден", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialogView.findViewById<android.view.View>(R.id.btnManageCancel).setOnClickListener {
            dialog.dismiss()
        }
    }

    fun parseTextWithRegex(text: String): Map<String, String?> {
        val nameRegex = Regex("""Препарат:\s*(.*)""", RegexOption.IGNORE_CASE)
        val dosageRegex = Regex("""Дозировка:\s*(\d+\s*(?:мг|мл|таб|г))""", RegexOption.IGNORE_CASE)
        val typeRegex = Regex("""(таблетки|таблетка|табл?\.?|капсулы|капс?\.?|сироп|ампулы|амп?\.?|раствор|раств?\.?|мазь|свечи|свеч?\.?)""", RegexOption.IGNORE_CASE)
        return mapOf(
            "name" to nameRegex.find(text)?.groupValues?.get(1)?.trim(),
            "dosage" to dosageRegex.find(text)?.groupValues?.get(1)?.trim(),
            "type" to typeRegex.find(text)?.groupValues?.get(1)?.trim()
        )
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
            emptyView.visibility = android.view.View.VISIBLE
            recyclerView.visibility = android.view.View.GONE
        } else {
            emptyView.visibility = android.view.View.GONE
            recyclerView.visibility = android.view.View.VISIBLE
        }
    }

    private fun normalizeType(rawType: String): String {
        val clean = rawType.lowercase()
        return when {
            clean.contains("таб") -> "Таблетки"
            clean.contains("капс") -> "Капсулы"
            clean.contains("амп") -> "Ампулы"
            clean.contains("раств") -> "Раствор"
            clean.contains("свеч") -> "Свечи"
            else -> rawType.replaceFirstChar { it.uppercase() }
        }
    }

    private fun processDocxFile(uri: Uri) {
        try {
            val extractedText = DocxParser.extractTextFromDocx(this, uri)
            if (extractedText.contains("Ошибка")) {
                Toast.makeText(this, "Не удалось прочитать файл. Убедитесь, что это .docx", Toast.LENGTH_LONG).show()
                return
            }

            val result = parseTextWithRegex(extractedText)

            result["name"]?.let { activeEtName?.setText(it) }
            result["dosage"]?.let { activeEtDosage?.setText(it) }
            result["type"]?.let { activeEtType?.setText(normalizeType(it)) }

            if (result["name"] == null) {
                Toast.makeText(this, "Не удалось автоматически распознать данные. Введите вручную.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Ошибка при обработке файла рецепта", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("MED_CHANNEL_V2", "Уведомления аптечки", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Канал для напоминаний о приеме и сроках годности"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun setupExpiryWorker() {
        val expiryWorkRequest = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("MedicationExpiryCheck", ExistingPeriodicWorkPolicy.KEEP, expiryWorkRequest)
    }

    private fun showAddMedicationDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_medication, null)

        val tilName = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilName)
        val tilType = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilType)
        val tilDosage = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilDosage)

        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etType = dialogView.findViewById<EditText>(R.id.etType)
        val etDosage = dialogView.findViewById<EditText>(R.id.etDosage)
        val btnExpiryDate = dialogView.findViewById<Button>(R.id.btnExpiryDate)
        val btnLoadDocx = dialogView.findViewById<Button>(R.id.btnLoadDocx)
        val btnReminderTime = dialogView.findViewById<Button>(R.id.btnReminderTime)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSave)

        activeEtName = etName
        activeEtType = etType
        activeEtDosage = etDosage

        var selectedExpiryDateMillis: Long = 0
        var selectedReminderTimeMillis: Long? = null

        etName.addTextChangedListener { tilName.error = null }
        etType.addTextChangedListener { tilType.error = null }
        etDosage.addTextChangedListener { tilDosage.error = null }

        btnLoadDocx.setOnClickListener {
            docxPickerLauncher.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        }

        btnReminderTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(calendar.get(Calendar.HOUR_OF_DAY))
                .setMinute(calendar.get(Calendar.MINUTE))
                .setTitleText("Время напоминания")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val reminderCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    set(Calendar.MINUTE, timePicker.minute)
                    set(Calendar.SECOND, 0)
                    if (timeInMillis < System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                selectedReminderTimeMillis = reminderCalendar.timeInMillis
                btnReminderTime.text = String.format("Напоминание: %02d:%02d", timePicker.hour, timePicker.minute)
            }
            timePicker.show(supportFragmentManager, "time_picker")
        }

        btnExpiryDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Выберите срок годности")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                selectedExpiryDateMillis = selection
                val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                btnExpiryDate.text = "Выбрано: ${format.format(Date(selection))}"
            }
            datePicker.show(supportFragmentManager, "date_picker")
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.setOnDismissListener {
            activeEtName = null
            activeEtType = null
            activeEtDosage = null
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            btnSave.isEnabled = false

            val name = etName.text.toString().trim()
            val type = etType.text.toString().trim()
            val dosage = etDosage.text.toString().trim()

            tilName.error = null
            tilType.error = null
            tilDosage.error = null

            val validContentRegex = Regex(".*[a-zA-Zа-яА-ЯёЁ0-9].*")

            if (name.isEmpty()) {
                tilName.error = "Введите название препарата"
                etName.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }
            if (!name.matches(validContentRegex)) {
                tilName.error = "Название не должно состоять только из символов"
                etName.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }
            if (name.length > 30) {
                tilName.error = "Максимум 30 символов"
                etName.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }

            if (type.isEmpty()) {
                tilType.error = "Укажите форму (например, таблетки)"
                etType.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }
            if (!type.matches(validContentRegex)) {
                tilType.error = "Пожалуйста, используйте буквы или цифры"
                etType.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }
            if (type.length > 20) {
                tilType.error = "Максимум 20 символов"
                etType.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }

            if (dosage.isEmpty()) {
                tilDosage.error = "Укажите дозировку"
                etDosage.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }
            if (!dosage.matches(validContentRegex)) {
                tilDosage.error = "Дозировка должна содержать цифры или буквы"
                etDosage.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }
            if (dosage.length > 20) {
                tilDosage.error = "Максимум 20 символов"
                etDosage.requestFocus()
                btnSave.isEnabled = true
                return@setOnClickListener
            }

            if (selectedExpiryDateMillis <= 0) {
                Toast.makeText(this, "Пожалуйста, укажите срок годности", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                return@setOnClickListener
            }

            if (selectedReminderTimeMillis != null && !checkExactAlarmPermission()) {
                btnSave.isEnabled = true
                return@setOnClickListener
            }

            val newMed = Medication(
                name = name,
                type = type,
                dosage = dosage,
                expiryDateMillis = selectedExpiryDateMillis,
                reminderTimeMillis = selectedReminderTimeMillis
            )

            repository.saveOrUpdateMedication(newMed)
            alarmScheduler.scheduleExpiryAlarm(newMed)
            alarmScheduler.scheduleEarlyExpiryAlarm(newMed)

            if (selectedExpiryDateMillis < System.currentTimeMillis()) {
                Toast.makeText(this, "Внимание! Вы добавили просроченный препарат!", Toast.LENGTH_LONG).show()
            }

            if (selectedReminderTimeMillis != null) {
                alarmScheduler.scheduleAlarm(newMed)
            }

            adapter.updateData(repository.getMedications())
            updateEmptyState()
            dialog.dismiss()
        }
    }

    private fun showSortDialog() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_sort, null)
        bottomSheetDialog.setContentView(view)

        view.findViewById<LinearLayout>(R.id.btnSortName).setOnClickListener {
            adapter.updateData(repository.getMedications().sortedBy { it.name.lowercase() })
            bottomSheetDialog.dismiss()
        }

        view.findViewById<LinearLayout>(R.id.btnSortExpiry).setOnClickListener {
            adapter.updateData(repository.getMedications().sortedBy { it.expiryDateMillis })
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }
}