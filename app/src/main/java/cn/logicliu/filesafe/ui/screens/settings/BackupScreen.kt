package cn.logicliu.filesafe.ui.screens.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.logicliu.filesafe.data.FileDataStore
import cn.logicliu.filesafe.data.FileSafeDatabase
import cn.logicliu.filesafe.data.repository.FileRepository
import cn.logicliu.filesafe.data.repository.OrphanedFilesInfo
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.service.ThumbnailManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupState(
    val isLoading: Boolean = false,
    val progress: Float = 0f,
    val status: String = ""
)

class BackupViewModel(
    private val context: Context,
    private val database: FileSafeDatabase,
    private val cryptoManager: CryptoManager,
    private val passwordManager: PasswordManager,
    private val fileDataStore: FileDataStore,
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow(BackupState())
    val restoreState: StateFlow<BackupState> = _restoreState.asStateFlow()

    val lastBackupTime = passwordManager.lastBackupTime

    fun createBackup(password: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _backupState.value = BackupState(isLoading = true, progress = 0f, status = "正在清理孤立文件...")

            try {
                fileRepository.cleanOrphanedFiles()

                _backupState.value = _backupState.value.copy(progress = 0.02f, status = "正在准备备份...")

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFileName = "FileSafe_Backup_$timestamp"
                val encryptedFileName = if (password != null) "${backupFileName}.aes" else "${backupFileName}.zip"

                withContext(Dispatchers.IO) {
                    database.close()

                    val tempZipFile = File(context.cacheDir, "$backupFileName.zip")

                    _backupState.value = _backupState.value.copy(progress = 0.05f, status = "正在创建压缩文件...")

                    ZipOutputStream(FileOutputStream(tempZipFile)).use { zipOut ->
                        val filesDir = context.filesDir
                        val allFiles = mutableListOf<File>()
                        var processed = 0

                        addDatabaseFiles(allFiles)
                        addDataStoreFiles(allFiles, filesDir)
                        addEncryptedFiles(allFiles, filesDir)
                        addThumbnailFiles(allFiles, filesDir)

                        val totalFiles = allFiles.size

                        allFiles.forEach { file ->
                            val entryPath = getEntryPath(file, filesDir)
                            zipOut.putNextEntry(ZipEntry(entryPath))
                            FileInputStream(file).use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                            processed++
                            _backupState.value = _backupState.value.copy(
                                progress = 0.05f + (processed.toFloat() / totalFiles) * 0.75f,
                                status = "正在压缩... ($processed/$totalFiles)"
                            )
                        }
                    }

                    _backupState.value = _backupState.value.copy(progress = 0.85f, status = "正在保存到Downloads...")

                    val resultFile = if (password != null) {
                        val encryptedData = cryptoManager.encryptFile(tempZipFile, password)
                        val result = File(context.cacheDir, encryptedFileName)
                        result.writeBytes(encryptedData)
                        tempZipFile.delete()
                        result
                    } else {
                        tempZipFile
                    }

                    saveToDownloads(resultFile, if (password != null) "application/octet-stream" else "application/zip")
                    resultFile.delete()

                    passwordManager.setLastBackupTime(System.currentTimeMillis())

                    _backupState.value = _backupState.value.copy(
                        progress = 1f,
                        status = "备份完成"
                    )

                    val downloadFileName = if (password != null) encryptedFileName else "$backupFileName.zip"
                    onSuccess(downloadFileName)
                }
            } catch (e: Exception) {
                _backupState.value = _backupState.value.copy(
                    isLoading = false,
                    status = "备份失败: ${e.message}"
                )
                onError(e.message ?: "未知错误")
            }

            _backupState.value = _backupState.value.copy(isLoading = false)
        }
    }

    fun restoreBackup(
        inputStream: java.io.InputStream,
        password: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _restoreState.value = BackupState(isLoading = true, progress = 0f, status = "正在准备恢复...")

            try {
                withContext(Dispatchers.IO) {
                    _restoreState.value = _restoreState.value.copy(progress = 0.1f, status = "正在读取备份文件...")

                    val tempFile = File(context.cacheDir, "restore_temp.zip")

                    if (password != null) {
                        val decryptedData = cryptoManager.decryptFile(inputStream.readBytes(), password)
                        tempFile.writeBytes(decryptedData)
                    } else {
                        FileOutputStream(tempFile).use { output ->
                            inputStream.copyTo(output)
                        }
                    }

                    _restoreState.value = _restoreState.value.copy(progress = 0.2f, status = "正在关闭数据库...")

                    database.close()

                    _restoreState.value = _restoreState.value.copy(progress = 0.25f, status = "正在解压...")

                    val filesDir = context.filesDir
                    val entries = mutableListOf<ZipEntryInfo>()

                    ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            entries.add(ZipEntryInfo(entry.name, entry.isDirectory))
                            if (!entry.isDirectory) {
                                val targetFile = resolveTargetFile(entry.name, filesDir)
                                if (targetFile != null) {
                                    targetFile.parentFile?.mkdirs()
                                    FileOutputStream(targetFile).use { output ->
                                        zipIn.copyTo(output)
                                    }
                                }
                            }
                            entry = zipIn.nextEntry
                        }
                    }

                    tempFile.delete()

                    _restoreState.value = _restoreState.value.copy(
                        progress = 1f,
                        status = "恢复完成"
                    )

                    onSuccess()
                }
            } catch (e: Exception) {
                _restoreState.value = _restoreState.value.copy(
                    isLoading = false,
                    status = "恢复失败: ${e.message}"
                )
                onError(e.message ?: "未知错误")
            }

            _restoreState.value = _restoreState.value.copy(isLoading = false)
        }
    }

    private val databaseDir = File(context.filesDir.parent, "databases")

    private fun addDatabaseFiles(allFiles: MutableList<File>) {
        val dbNames = listOf(
            FileSafeDatabase.DATABASE_NAME,
            "${FileSafeDatabase.DATABASE_NAME}-shm",
            "${FileSafeDatabase.DATABASE_NAME}-wal"
        )
        dbNames.forEach { name ->
            val file = File(databaseDir, name)
            if (file.exists()) {
                allFiles.add(file)
            }
        }
    }

    private fun addDataStoreFiles(allFiles: MutableList<File>, filesDir: File) {
        val dataStoreNames = listOf("file_data", "settings", "security_settings", "security_questions")
        dataStoreNames.forEach { name ->
            val dataStoreDir = File(filesDir, "datastore/$name")
            if (dataStoreDir.exists()) {
                dataStoreDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        allFiles.add(file)
                    }
                }
            }
        }
    }

    private suspend fun addEncryptedFiles(allFiles: MutableList<File>, filesDir: File) {
        val encryptedDir = File(filesDir, "encrypted_files")
        if (!encryptedDir.exists()) return

        val validFiles = fileDataStore.files.first()
        val validEncryptedPaths = validFiles.map { it.encryptedPath }.toSet()

        encryptedDir.walkTopDown().forEach { file ->
            if (file.isFile && file.absolutePath in validEncryptedPaths) {
                allFiles.add(file)
            }
        }
    }

    private suspend fun addThumbnailFiles(allFiles: MutableList<File>, filesDir: File) {
        val thumbnailDir = File(filesDir, ThumbnailManager.THUMBNAIL_DIR)
        if (!thumbnailDir.exists()) return

        val validFileIds = fileDataStore.files.first().map { it.id }.toSet()

        thumbnailDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val fileId = file.nameWithoutExtension.toLongOrNull()
                if (fileId != null && fileId in validFileIds) {
                    allFiles.add(file)
                }
            }
        }
    }

    private fun addFilesRecursively(dir: File, allFiles: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                allFiles.add(file)
            } else if (file.isDirectory) {
                addFilesRecursively(file, allFiles)
            }
        }
    }

    private fun getEntryPath(file: File, filesDir: File): String {
        val dbPath = file.absolutePath.removePrefix(databaseDir.absolutePath + "/")
        if (dbPath != file.absolutePath) {
            return "database/$dbPath"
        }

        val relativePath = file.absolutePath.removePrefix(filesDir.absolutePath + "/")

        return when {
            relativePath.startsWith("datastore/") -> "preferences/$relativePath"
            relativePath.startsWith("encrypted_files/") -> "encrypted/${relativePath.removePrefix("encrypted_files/")}"
            relativePath.startsWith("thumbnails/") -> "thumbnails/${relativePath.removePrefix("thumbnails/")}"
            else -> relativePath
        }
    }

    private fun resolveTargetFile(entryName: String, filesDir: File): File? {
        return when {
            entryName.startsWith("database/") -> {
                val dbFileName = entryName.removePrefix("database/")
                File(databaseDir, dbFileName)
            }
            entryName.startsWith("preferences/") -> {
                val prefPath = entryName.removePrefix("preferences/")
                File(filesDir, prefPath)
            }
            entryName.startsWith("encrypted/") -> {
                val encPath = entryName.removePrefix("encrypted/")
                File(filesDir, "encrypted_files/$encPath")
            }
            entryName.startsWith("thumbnails/") -> {
                val thumbPath = entryName.removePrefix("thumbnails/")
                File(filesDir, "thumbnails/$thumbPath")
            }
            else -> null
        }
    }

    private fun saveToDownloads(file: File, mimeType: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            contentValues.clear()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        }
    }

    private data class ZipEntryInfo(val name: String, val isDirectory: Boolean)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    context: Context = LocalContext.current,
    database: FileSafeDatabase = remember { FileSafeDatabase.getInstance(context) },
    cryptoManager: CryptoManager = remember { CryptoManager(context) },
    passwordManager: PasswordManager = remember { PasswordManager(context, cryptoManager) },
    fileDataStore: FileDataStore = remember { FileDataStore.getInstance(context) },
    fileRepository: FileRepository = remember {
        FileRepository(context, fileDataStore, cryptoManager,
            cn.logicliu.filesafe.security.SecuritySettingsManager(context))
    }
) {
    val viewModel = remember {
        BackupViewModel(context, database, cryptoManager, passwordManager, fileDataStore, fileRepository)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backupState by viewModel.backupState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState(initial = 0L)

    var showPasswordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var usePassword by remember { mutableStateOf(false) }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var restorePasswordVisible by remember { mutableStateOf(false) }
    var isRestorePassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showFeatureDevDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingRestoreUri = it
            showRestoreConfirmDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("备份与恢复") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (lastBackupTime > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "上次备份",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatBackupTime(lastBackupTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "创建备份",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "将所有数据（加密文件、数据库、设置、缩略图）打包保存到Downloads目录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = usePassword,
                            onCheckedChange = { usePassword = it }
                        )
                        Text(
                            text = "使用密码保护",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (backupState.isLoading) {
                        LinearProgressIndicator(
                            progress = { backupState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = backupState.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(
                            onClick = { showFeatureDevDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("开始备份")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "恢复备份",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "从备份文件恢复您的所有数据和设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isRestorePassword,
                            onCheckedChange = { isRestorePassword = it }
                        )
                        Text(
                            text = "备份文件有密码保护",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (isRestorePassword) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = restorePassword,
                            onValueChange = { restorePassword = it },
                            label = { Text("备份密码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { restorePasswordVisible = !restorePasswordVisible }) {
                                    Icon(
                                        imageVector = if (restorePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (restorePasswordVisible) "隐藏密码" else "显示密码"
                                    )
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (restoreState.isLoading) {
                        LinearProgressIndicator(
                            progress = { restoreState.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = restoreState.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedButton(
                            onClick = { showFeatureDevDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("选择备份文件")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "注意",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "恢复备份将覆盖当前所有数据，此操作不可撤销。请在恢复前确保已备份当前数据。恢复完成后应用将自动重启。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("设置备份密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                                Icon(
                                    imageVector = if (backupPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (backupPasswordVisible) "隐藏密码" else "显示密码"
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (password == confirmPassword && password.isNotEmpty()) {
                            showPasswordDialog = false
                            viewModel.createBackup(
                                password = password,
                                onSuccess = { fileName ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("备份已保存: $fileName")
                                    }
                                    password = ""
                                    confirmPassword = ""
                                },
                                onError = { error ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("备份失败: $error")
                                    }
                                }
                            )
                        }
                    },
                    enabled = password.isNotEmpty() && password == confirmPassword
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                pendingRestoreUri = null
            },
            title = { Text("确认恢复") },
            text = {
                Text("恢复备份将覆盖当前所有数据（包括加密文件、数据库和设置），此操作不可撤销。确定要继续吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri?.let { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    viewModel.restoreBackup(
                                        inputStream = inputStream,
                                        password = if (isRestorePassword) restorePassword else null,
                                        onSuccess = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("恢复成功，正在重启应用...")
                                                kotlinx.coroutines.delay(1500)
                                                restartApp(context)
                                            }
                                        },
                                        onError = { error ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("恢复失败: $error")
                                            }
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("选择文件失败: ${e.message}")
                                }
                            }
                        }
                        pendingRestoreUri = null
                    }
                ) {
                    Text("确定恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreConfirmDialog = false
                    pendingRestoreUri = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (showFeatureDevDialog) {
        AlertDialog(
            onDismissRequest = { showFeatureDevDialog = false },
            title = { Text("功能开发中") },
            text = {
                Text("备份与恢复功能正在开发中，敬请期待后续版本更新。")
            },
            confirmButton = {
                TextButton(onClick = { showFeatureDevDialog = false }) {
                    Text("知道了")
                }
            }
        )
    }
}

private fun formatBackupTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}
