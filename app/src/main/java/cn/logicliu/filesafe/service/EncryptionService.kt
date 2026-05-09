package cn.logicliu.filesafe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.logicliu.filesafe.MainActivity
import cn.logicliu.filesafe.R
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.security.EncryptionMode
import cn.logicliu.filesafe.security.SecuritySettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class FileOperationProgress(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val fileName: String = "",
    val operation: FileTaskOperation = FileTaskOperation.IMPORT,
    val result: OperationResult? = null,
    val errorMessage: String? = null
)

enum class FileTaskOperation {
    IMPORT, EXPORT
}

enum class OperationResult {
    SUCCESS, ERROR, CANCELLED
}

object ProgressManager {
    private val listeners = mutableListOf<(FileOperationProgress) -> Unit>()
    
    fun addListener(listener: (FileOperationProgress) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (FileOperationProgress) -> Unit) {
        listeners.remove(listener)
    }
    
    fun notifyProgress(progress: FileOperationProgress) {
        listeners.forEach { it(progress) }
    }
}

class EncryptionService : Service() {

    private lateinit var cryptoManager: CryptoManager
    private lateinit var securitySettingsManager: SecuritySettingsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    companion object {
        const val CHANNEL_ID = "file_operation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_IMPORT = "cn.logicliu.filesafe.IMPORT"
        const val ACTION_EXPORT = "cn.logicliu.filesafe.EXPORT"
        const val ACTION_CANCEL = "cn.logicliu.filesafe.CANCEL"

        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_FILE_ID = "file_id"
        const val EXTRA_FOLDER_ID = "folder_id"
        const val EXTRA_TEMP_FILE_PATH = "temp_file_path"

        fun startImport(context: Context, sourceUri: Uri, tempFilePath: String, folderId: Long? = null) {
            val intent = Intent(context, EncryptionService::class.java).apply {
                action = ACTION_IMPORT
                putExtra(EXTRA_SOURCE_URI, sourceUri)
                putExtra(EXTRA_TEMP_FILE_PATH, tempFilePath)
                folderId?.let { putExtra(EXTRA_FOLDER_ID, it) }
            }
            context.startForegroundService(intent)
        }

        fun startExport(context: Context, fileId: Long) {
            val intent = Intent(context, EncryptionService::class.java).apply {
                action = ACTION_EXPORT
                putExtra(EXTRA_FILE_ID, fileId)
            }
            context.startForegroundService(intent)
        }

        fun cancelOperation(context: Context) {
            val intent = Intent(context, EncryptionService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        cryptoManager = CryptoManager(applicationContext)
        securitySettingsManager = SecuritySettingsManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_IMPORT -> {
                val sourceUri = intent.getParcelableExtra<Uri>(EXTRA_SOURCE_URI) ?: return START_NOT_STICKY
                val tempFilePath = intent.getStringExtra(EXTRA_TEMP_FILE_PATH) ?: return START_NOT_STICKY
                val folderId = if (intent.hasExtra(EXTRA_FOLDER_ID)) intent.getLongExtra(EXTRA_FOLDER_ID, -1).takeIf { it != -1L } else null

                startForeground(NOTIFICATION_ID, createNotification("正在导入文件...", 0f))
                startImport(sourceUri, tempFilePath, folderId)
            }
            ACTION_EXPORT -> {
                val fileId = intent.getLongExtra(EXTRA_FILE_ID, -1)
                if (fileId == -1L) return START_NOT_STICKY

                startForeground(NOTIFICATION_ID, createNotification("正在导出文件...", 0f))
                startExport(fileId)
            }
            ACTION_CANCEL -> {
                currentJob?.cancel()
                ProgressManager.notifyProgress(
                    FileOperationProgress(result = OperationResult.CANCELLED)
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "文件操作服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示文件导入/导出进度"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, progress: Float): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EncryptionService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消", cancelIntent)

        if (progress > 0) {
            builder.setProgress(100, (progress * 100).toInt(), false)
        } else {
            builder.setProgress(100, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(title: String, progress: Float) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress))
    }

    private fun startImport(sourceUri: Uri, tempFilePath: String, folderId: Long?) {
        currentJob = serviceScope.launch {
            try {
                val tempFile = File(tempFilePath)
                val fileName = getFileNameFromUri(sourceUri) ?: "file_${System.currentTimeMillis()}"
                
                ProgressManager.notifyProgress(
                    FileOperationProgress(
                        isRunning = true,
                        progress = 0f,
                        fileName = fileName,
                        operation = FileTaskOperation.IMPORT
                    )
                )
                updateNotification("正在导入: $fileName", 0f)

                copyUriToFile(sourceUri, tempFile) { progress ->
                    val adjustedProgress = progress * 0.3f
                    ProgressManager.notifyProgress(
                        FileOperationProgress(
                            isRunning = true,
                            progress = adjustedProgress,
                            fileName = fileName,
                            operation = FileTaskOperation.IMPORT
                        )
                    )
                    updateNotification("正在复制文件: $fileName", adjustedProgress)
                }

                val encryptionMode = securitySettingsManager.encryptionMode.first()
                val encryptedDir = File(applicationContext.filesDir, "encrypted_files").apply { mkdirs() }
                
                val (finalFile, isEncrypted) = if (encryptionMode == EncryptionMode.ENCRYPT) {
                    val encryptedFileName = "${UUID.randomUUID()}.enc"
                    val encryptedFile = File(encryptedDir, encryptedFileName)

                    val encryptSuccess = cryptoManager.encryptFile(tempFile, encryptedFile) { progress ->
                        val adjustedProgress = 0.3f + progress * 0.7f
                        ProgressManager.notifyProgress(
                            FileOperationProgress(
                                isRunning = true,
                                progress = adjustedProgress,
                                fileName = fileName,
                                operation = FileTaskOperation.IMPORT
                            )
                        )
                        updateNotification("正在加密: $fileName", adjustedProgress)
                    }

                    tempFile.delete()

                    if (!encryptSuccess) {
                        throw Exception("加密失败")
                    }
                    
                    Pair(encryptedFile, true)
                } else {
                    val hiddenFileName = ".${UUID.randomUUID()}"
                    val hiddenFile = File(encryptedDir, hiddenFileName)
                    
                    tempFile.copyTo(hiddenFile, overwrite = true)
                    tempFile.delete()
                    
                    ProgressManager.notifyProgress(
                        FileOperationProgress(
                            isRunning = true,
                            progress = 1f,
                            fileName = fileName,
                            operation = FileTaskOperation.IMPORT
                        )
                    )
                    updateNotification("导入完成: $fileName", 1f)
                    
                    Pair(hiddenFile, false)
                }

                val fileDataStore = cn.logicliu.filesafe.data.FileDataStore.getInstance(applicationContext)
                val fileEntity = cn.logicliu.filesafe.data.entity.FileItemEntity(
                    name = fileName,
                    path = fileName,
                    encryptedPath = finalFile.absolutePath,
                    size = finalFile.length(),
                    mimeType = applicationContext.contentResolver.getType(sourceUri),
                    folderId = folderId,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    isEncrypted = isEncrypted
                )
                fileDataStore.insertFile(fileEntity)

                ProgressManager.notifyProgress(
                    FileOperationProgress(
                        isRunning = false,
                        progress = 1f,
                        fileName = fileName,
                        operation = FileTaskOperation.IMPORT,
                        result = OperationResult.SUCCESS
                    )
                )
                updateNotification("导入完成: $fileName", 1f)

            } catch (e: Exception) {
                ProgressManager.notifyProgress(
                    FileOperationProgress(
                        isRunning = false,
                        operation = FileTaskOperation.IMPORT,
                        result = OperationResult.ERROR,
                        errorMessage = e.message
                    )
                )
                updateNotification("导入失败: ${e.message}", 0f)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startExport(fileId: Long) {
        currentJob = serviceScope.launch {
            try {
                val fileDataStore = cn.logicliu.filesafe.data.FileDataStore.getInstance(applicationContext)
                val fileEntity = fileDataStore.getFileById(fileId).first() ?: throw Exception("文件不存在")
                val encryptedFile = File(fileEntity.encryptedPath)
                
                if (!encryptedFile.exists()) {
                    throw Exception("加密文件不存在")
                }

                ProgressManager.notifyProgress(
                    FileOperationProgress(
                        isRunning = true,
                        progress = 0f,
                        fileName = fileEntity.name,
                        operation = FileTaskOperation.EXPORT
                    )
                )
                updateNotification("正在导出: ${fileEntity.name}", 0f)

                val tempDir = File(applicationContext.cacheDir, "temp").apply { mkdirs() }
                val decryptedFile = File(tempDir, fileEntity.name)

                if (fileEntity.isEncrypted) {
                    val decryptSuccess = cryptoManager.decryptFile(encryptedFile, decryptedFile) { progress ->
                        ProgressManager.notifyProgress(
                            FileOperationProgress(
                                isRunning = true,
                                progress = progress,
                                fileName = fileEntity.name,
                                operation = FileTaskOperation.EXPORT
                            )
                        )
                        updateNotification("正在解密: ${fileEntity.name}", progress)
                    }

                    if (!decryptSuccess) {
                        throw Exception("解密失败")
                    }
                } else {
                    encryptedFile.copyTo(decryptedFile, overwrite = true)
                    
                    ProgressManager.notifyProgress(
                        FileOperationProgress(
                            isRunning = true,
                            progress = 1f,
                            fileName = fileEntity.name,
                            operation = FileTaskOperation.EXPORT
                        )
                    )
                    updateNotification("导出完成: ${fileEntity.name}", 1f)
                }

                ProgressManager.notifyProgress(
                    FileOperationProgress(
                        isRunning = false,
                        progress = 1f,
                        fileName = fileEntity.name,
                        operation = FileTaskOperation.EXPORT,
                        result = OperationResult.SUCCESS
                    )
                )
                updateNotification("导出完成: ${fileEntity.name}", 1f)

                ExportTempFileHolder.tempFile = decryptedFile

            } catch (e: Exception) {
                ProgressManager.notifyProgress(
                    FileOperationProgress(
                        isRunning = false,
                        operation = FileTaskOperation.EXPORT,
                        result = OperationResult.ERROR,
                        errorMessage = e.message
                    )
                )
                updateNotification("导出失败: ${e.message}", 0f)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        when (uri.scheme) {
            "content" -> {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
            }
            "file" -> {
                fileName = uri.lastPathSegment
            }
        }
        return fileName
    }

    private suspend fun copyUriToFile(uri: Uri, targetFile: File, progressCallback: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(uri) ?: return@withContext
        val totalSize = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        var bytesCopied = 0L

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesCopied += bytesRead
                    if (totalSize > 0) {
                        progressCallback(bytesCopied.toFloat() / totalSize)
                    }
                }
            }
        }
    }
}

object ExportTempFileHolder {
    var tempFile: File? = null
}
