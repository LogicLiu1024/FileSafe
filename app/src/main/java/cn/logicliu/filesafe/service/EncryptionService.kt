package cn.logicliu.filesafe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cn.logicliu.filesafe.MainActivity
import cn.logicliu.filesafe.R
import cn.logicliu.filesafe.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class EncryptionProgress(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val currentFile: String = "",
    val totalFiles: Int = 0,
    val processedFiles: Int = 0,
    val operation: EncryptionOperation = EncryptionOperation.ENCRYPT
)

enum class EncryptionOperation {
    ENCRYPT, DECRYPT
}

enum class EncryptionResult {
    SUCCESS, ERROR, CANCELLED
}

class EncryptionService : Service() {

    private lateinit var cryptoManager: CryptoManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _progress = MutableStateFlow(EncryptionProgress())
    val progress: StateFlow<EncryptionProgress> = _progress.asStateFlow()

    private val _result = MutableStateFlow<EncryptionResult?>(null)
    val result: StateFlow<EncryptionResult?> = _result.asStateFlow()

    companion object {
        const val CHANNEL_ID = "encryption_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_ENCRYPT = "cn.logicliu.filesafe.ENCRYPT"
        const val ACTION_DECRYPT = "cn.logicliu.filesafe.DECRYPT"
        const val ACTION_CANCEL = "cn.logicliu.filesafe.CANCEL"

        const val EXTRA_SOURCE_DIR = "source_dir"
        const val EXTRA_TARGET_DIR = "target_dir"
        const val EXTRA_PASSWORD = "password"

        fun startEncryption(context: Context, sourceDir: String, targetDir: String, password: String) {
            val intent = Intent(context, EncryptionService::class.java).apply {
                action = ACTION_ENCRYPT
                putExtra(EXTRA_SOURCE_DIR, sourceDir)
                putExtra(EXTRA_TARGET_DIR, targetDir)
                putExtra(EXTRA_PASSWORD, password)
            }
            context.startForegroundService(intent)
        }

        fun startDecryption(context: Context, sourceDir: String, targetDir: String, password: String) {
            val intent = Intent(context, EncryptionService::class.java).apply {
                action = ACTION_DECRYPT
                putExtra(EXTRA_SOURCE_DIR, sourceDir)
                putExtra(EXTRA_TARGET_DIR, targetDir)
                putExtra(EXTRA_PASSWORD, password)
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENCRYPT -> {
                val sourceDir = intent.getStringExtra(EXTRA_SOURCE_DIR) ?: return START_NOT_STICKY
                val targetDir = intent.getStringExtra(EXTRA_TARGET_DIR) ?: return START_NOT_STICKY
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return START_NOT_STICKY

                startForeground(NOTIFICATION_ID, createNotification("正在加密文件...", 0f))
                startEncryption(sourceDir, targetDir, password)
            }
            ACTION_DECRYPT -> {
                val sourceDir = intent.getStringExtra(EXTRA_SOURCE_DIR) ?: return START_NOT_STICKY
                val targetDir = intent.getStringExtra(EXTRA_TARGET_DIR) ?: return START_NOT_STICKY
                val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return START_NOT_STICKY

                startForeground(NOTIFICATION_ID, createNotification("正在解密文件...", 0f))
                startDecryption(sourceDir, targetDir, password)
            }
            ACTION_CANCEL -> {
                currentJob?.cancel()
                _result.value = EncryptionResult.CANCELLED
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
            "文件加密服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示文件加密/解密进度"
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

    private fun startEncryption(sourceDir: String, targetDir: String, password: String) {
        currentJob = serviceScope.launch {
            try {
                val source = File(sourceDir)
                val target = File(targetDir)

                if (!target.exists()) {
                    target.mkdirs()
                }

                val files = source.listFiles()?.filter { it.isFile } ?: emptyList()
                val totalFiles = files.size

                _progress.value = EncryptionProgress(
                    isRunning = true,
                    progress = 0f,
                    currentFile = "",
                    totalFiles = totalFiles,
                    processedFiles = 0,
                    operation = EncryptionOperation.ENCRYPT
                )

                files.forEachIndexed { index, file ->
                    val progress = (index + 1).toFloat() / totalFiles
                    _progress.value = _progress.value.copy(
                        progress = progress,
                        currentFile = file.name,
                        processedFiles = index + 1
                    )

                    updateNotification("正在加密: ${file.name}", progress)

                    val encryptedFile = File(target, "${file.name}.enc")
                    withContext(Dispatchers.IO) {
                        val encryptedData = cryptoManager.encryptFile(file, password)
                        FileOutputStream(encryptedFile).use { it.write(encryptedData) }
                    }
                }

                _progress.value = _progress.value.copy(
                    isRunning = false,
                    progress = 1f
                )
                _result.value = EncryptionResult.SUCCESS

                updateNotification("加密完成", 1f)

            } catch (e: Exception) {
                _progress.value = _progress.value.copy(isRunning = false)
                _result.value = EncryptionResult.ERROR
                updateNotification("加密失败: ${e.message}", 0f)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startDecryption(sourceDir: String, targetDir: String, password: String) {
        currentJob = serviceScope.launch {
            try {
                val source = File(sourceDir)
                val target = File(targetDir)

                if (!target.exists()) {
                    target.mkdirs()
                }

                val files = source.listFiles()?.filter { 
                    it.isFile && it.name.endsWith(".enc") 
                } ?: emptyList()
                val totalFiles = files.size

                _progress.value = EncryptionProgress(
                    isRunning = true,
                    progress = 0f,
                    currentFile = "",
                    totalFiles = totalFiles,
                    processedFiles = 0,
                    operation = EncryptionOperation.DECRYPT
                )

                files.forEachIndexed { index, file ->
                    val progress = (index + 1).toFloat() / totalFiles
                    val originalName = file.name.removeSuffix(".enc")

                    _progress.value = _progress.value.copy(
                        progress = progress,
                        currentFile = originalName,
                        processedFiles = index + 1
                    )

                    updateNotification("正在解密: $originalName", progress)

                    val decryptedFile = File(target, originalName)
                    withContext(Dispatchers.IO) {
                        val encryptedData = FileInputStream(file).use { it.readBytes() }
                        val decryptedData = cryptoManager.decryptFile(encryptedData, password)
                        FileOutputStream(decryptedFile).use { it.write(decryptedData) }
                    }
                }

                _progress.value = _progress.value.copy(
                    isRunning = false,
                    progress = 1f
                )
                _result.value = EncryptionResult.SUCCESS

                updateNotification("解密完成", 1f)

            } catch (e: Exception) {
                _progress.value = _progress.value.copy(isRunning = false)
                _result.value = EncryptionResult.ERROR
                updateNotification("解密失败: ${e.message}", 0f)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
