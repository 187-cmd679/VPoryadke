package com.example.aptechka
import android.content.Context
import android.net.Uri
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream

object DocxParser {
    fun extractTextFromDocx(context: Context, uri: Uri): String {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val document = XWPFDocument(inputStream)
            val text = StringBuilder()

            for (paragraph in document.paragraphs) {
                text.append(paragraph.text).append("\n")
            }
            inputStream?.close()
            text.toString()

        } catch (e: Exception) {
            e.printStackTrace()
            "Ошибка чтения файла рецепта."
        }
    }
}