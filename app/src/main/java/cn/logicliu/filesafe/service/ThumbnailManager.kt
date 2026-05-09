package cn.logicliu.filesafe.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailManager {

    private const val THUMBNAIL_DIR = "thumbnails"
    private const val THUMBNAIL_SIZE = 256
    private const val THUMBNAIL_QUALITY = 85
    private const val MAX_THUMBNAIL_FILE_SIZE = 100L * 1024 * 1024

    private fun getThumbnailDir(context: Context): File =
        File(context.filesDir, THUMBNAIL_DIR).apply { mkdirs() }

    fun getThumbnailFile(context: Context, fileId: Long): File =
        File(getThumbnailDir(context), "${fileId}.jpg")

    fun hasThumbnail(context: Context, fileId: Long): Boolean =
        getThumbnailFile(context, fileId).exists()

    suspend fun generateFromPlainFile(
        context: Context,
        plainFile: File,
        fileId: Long,
        mimeType: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = if (isVideoType(mimeType)) {
                extractVideoFrame(context, plainFile)
            } else {
                decodeImageBitmap(plainFile)
            }
            bitmap?.let { saveThumbnail(it, getThumbnailFile(context, fileId)) }
            bitmap != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun generateFromEncryptedFile(
        context: Context,
        cryptoManager: CryptoManager,
        encryptedFile: File,
        fileId: Long,
        mimeType: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (encryptedFile.length() > MAX_THUMBNAIL_FILE_SIZE) return@withContext false

            val tempDir = File(context.cacheDir, "thumb_temp").apply { mkdirs() }
            val tempFile = File(tempDir, "thumb_${fileId}.tmp")

            val decryptSuccess = cryptoManager.decryptFile(encryptedFile, tempFile)
            if (!decryptSuccess) return@withContext false

            val result = generateFromPlainFile(context, tempFile, fileId, mimeType)
            tempFile.delete()
            result
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteThumbnail(context: Context, fileId: Long): Boolean {
        val file = getThumbnailFile(context, fileId)
        return if (file.exists()) file.delete() else true
    }

    suspend fun ensureThumbnail(context: Context, entity: FileItemEntity): Boolean {
        if (!isThumbnailSupported(entity.name)) return false
        if (hasThumbnail(context, entity.id)) return true

        val storageFile = File(entity.encryptedPath)
        if (!storageFile.exists()) return false

        return if (entity.isEncrypted) {
            val cryptoManager = CryptoManager(context.applicationContext)
            generateFromEncryptedFile(context, cryptoManager, storageFile, entity.id, entity.mimeType)
        } else {
            generateFromPlainFile(context, storageFile, entity.id, entity.mimeType)
        }
    }

    private fun decodeImageBitmap(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            options.inJustDecodeBounds = false
            options.inSampleSize = calculateInSampleSize(
                options.outWidth, options.outHeight,
                THUMBNAIL_SIZE, THUMBNAIL_SIZE
            )

            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractVideoFrame(context: Context, videoFile: File): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.fromFile(videoFile))
            val bitmap = retriever.frameAtTime?.let { frame ->
                val width = frame.width
                val height = frame.height
                val scale = minOf(
                    THUMBNAIL_SIZE.toFloat() / width,
                    THUMBNAIL_SIZE.toFloat() / height
                )
                val newWidth = (width * scale).toInt().coerceAtLeast(1)
                val newHeight = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
            }
            retriever.release()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveThumbnail(bitmap: Bitmap, outputFile: File): Boolean {
        return try {
            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, fos)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun calculateInSampleSize(
        rawWidth: Int, rawHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun isVideoType(mimeType: String?): Boolean {
        return mimeType != null && mimeType.startsWith("video/")
    }

    fun isThumbnailSupported(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS || extension in VIDEO_EXTENSIONS
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv")
}