package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class TransactionAttachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val filePath: String,
    val fileSize: Long,
    val dateAdded: Long = System.currentTimeMillis()
) {
    val isPdf: Boolean
        get() = mimeType.equals("application/pdf", ignoreCase = true) || fileName.lowercase().endsWith(".pdf")

    val isImage: Boolean
        get() = mimeType.startsWith("image/", ignoreCase = true) ||
                fileName.lowercase().endsWith(".jpg") ||
                fileName.lowercase().endsWith(".jpeg") ||
                fileName.lowercase().endsWith(".png") ||
                fileName.lowercase().endsWith(".webp")
}

object TransactionAttachmentManager {

    fun getTransactionKey(itemType: Any, itemId: Long): String {
        return "${itemType.toString().lowercase()}_$itemId"
    }

    private fun getAttachmentsDirectory(context: Context, key: String): File {
        val dir = File(context.filesDir, "transaction_attachments/$key")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getMetadataFile(context: Context, key: String): File {
        return File(getAttachmentsDirectory(context, key), "metadata.json")
    }

    fun loadAttachments(context: Context, key: String): List<TransactionAttachment> {
        val metadataFile = getMetadataFile(context, key)
        if (!metadataFile.exists()) return emptyList()

        return try {
            val jsonStr = metadataFile.readText()
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<TransactionAttachment>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val attachment = TransactionAttachment(
                    id = obj.getString("id"),
                    fileName = obj.getString("fileName"),
                    mimeType = obj.getString("mimeType"),
                    filePath = obj.getString("filePath"),
                    fileSize = obj.optLong("fileSize", 0L),
                    dateAdded = obj.optLong("dateAdded", System.currentTimeMillis())
                )
                if (File(attachment.filePath).exists()) {
                    list.add(attachment)
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun saveAttachments(context: Context, key: String, list: List<TransactionAttachment>) {
        val metadataFile = getMetadataFile(context, key)
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("fileName", item.fileName)
                obj.put("mimeType", item.mimeType)
                obj.put("filePath", item.filePath)
                obj.put("fileSize", item.fileSize)
                obj.put("dateAdded", item.dateAdded)
                jsonArray.put(obj)
            }
            metadataFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addAttachmentFromUri(context: Context, key: String, uri: Uri): TransactionAttachment? {
        val contentResolver = context.contentResolver
        var fileName = "attachment_${System.currentTimeMillis()}"
        var fileSize = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) fileName = name
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var mimeType = contentResolver.getType(uri) ?: ""
        if (mimeType.isEmpty() || mimeType == "*/*") {
            val lower = fileName.lowercase()
            mimeType = when {
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }
        }

        if (!fileName.contains(".")) {
            fileName += if (mimeType == "application/pdf") ".pdf" else ".jpg"
        }

        val id = "att_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val dir = getAttachmentsDirectory(context, key)
        val sanitizedName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = File(dir, "${id}_$sanitizedName")

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (fileSize == 0L) {
                fileSize = destFile.length()
            }
            val attachment = TransactionAttachment(
                id = id,
                fileName = fileName,
                mimeType = mimeType,
                filePath = destFile.absolutePath,
                fileSize = fileSize,
                dateAdded = System.currentTimeMillis()
            )

            val currentList = loadAttachments(context, key).toMutableList()
            currentList.add(attachment)
            saveAttachments(context, key, currentList)

            return attachment
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun addAttachmentFromBitmap(context: Context, key: String, bitmap: Bitmap): TransactionAttachment? {
        val id = "att_${System.currentTimeMillis()}_${(1000..9999).random()}"
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())
        val fileName = "photo_$timeStamp.jpg"
        val dir = getAttachmentsDirectory(context, key)
        val destFile = File(dir, "${id}_$fileName")

        try {
            FileOutputStream(destFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            }
            val attachment = TransactionAttachment(
                id = id,
                fileName = fileName,
                mimeType = "image/jpeg",
                filePath = destFile.absolutePath,
                fileSize = destFile.length(),
                dateAdded = System.currentTimeMillis()
            )

            val currentList = loadAttachments(context, key).toMutableList()
            currentList.add(attachment)
            saveAttachments(context, key, currentList)

            return attachment
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun deleteAttachment(context: Context, key: String, attachmentId: String): List<TransactionAttachment> {
        val currentList = loadAttachments(context, key).toMutableList()
        val itemToRemove = currentList.find { it.id == attachmentId }
        if (itemToRemove != null) {
            val file = File(itemToRemove.filePath)
            if (file.exists()) {
                file.delete()
            }
            currentList.remove(itemToRemove)
            saveAttachments(context, key, currentList)
        }
        return currentList
    }

    fun createTempCameraUri(context: Context): Uri {
        val cacheDir = File(context.cacheDir, "camera_photos")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }

    fun renderPdfFirstPage(file: File): Bitmap? {
        if (!file.exists()) return null
        return try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) {
                renderer.close()
                descriptor.close()
                return null
            }
            val page = renderer.openPage(0)
            val width = (page.width * 1.5).toInt().coerceAtLeast(300)
            val height = (page.height * 1.5).toInt().coerceAtLeast(400)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            descriptor.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openPdfWithExternalApp(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No PDF viewer app found on this device", Toast.LENGTH_SHORT).show()
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
