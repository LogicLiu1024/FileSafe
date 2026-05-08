package cn.logicliu.filesafe.ui.screens.settings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import cn.logicliu.filesafe.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _backupState = MutableStateFlow(BackupState())
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow(BackupState())
    val restoreState: StateFlow<BackupState> = _restoreState.asStateFlow()

    fun createBackup(password: String?, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _backupState.value = BackupState(isLoading = true, progress = 0f, status = "正在准备备份...")
            
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFileName = "FileSafe_Backup_$timestamp"
                val encryptedFileName = if (password != null) "${backupFileName}.aes" else "${backupFileName}.zip"
                
                val filesDir = context.filesDir
                val encryptedDir = File(filesDir, "encrypted_files")
                val databaseFile = context.getDatabasePath("filesafe.db")
                val databaseShmFile = context.getDatabasePath("filesafe.db-shm")
                val databaseWalFile = context.getDatabasePath("filesafe.db-wal")
                
                withContext(Dispatchers.IO) {
                    val tempZipFile = File(context.cacheDir, "$backupFileName.zip")
                    
                    _backupState.value = _backupState.value.copy(progress = 0.1f, status = "正在创建压缩文件...")
                    
                    ZipOutputStream(FileOutputStream(tempZipFile)).use { zipOut ->
                        val encryptedFiles = encryptedDir.listFiles() ?: emptyArray()
                        val totalFiles = encryptedFiles.size + 4
                        var processed = 0
                        
                        if (databaseFile.exists()) {
                            zipOut.putNextEntry(ZipEntry("database/filesafe.db"))
                            FileInputStream(databaseFile).use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                        if (databaseShmFile.exists()) {
                            zipOut.putNextEntry(ZipEntry("database/filesafe.db-shm"))
                            FileInputStream(databaseShmFile).use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                        if (databaseWalFile.exists()) {
                            zipOut.putNextEntry(ZipEntry("database/filesafe.db-wal"))
                            FileInputStream(databaseWalFile).use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                        
                        processed = 4
                        _backupState.value = _backupState.value.copy(
                            progress = processed.toFloat() / totalFiles,
                            status = "正在压缩加密文件..."
                        )
                        
                        encryptedFiles.forEach { file ->
                            zipOut.putNextEntry(ZipEntry("encrypted/${file.name}"))
                            FileInputStream(file).use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                            processed++
                            _backupState.value = _backupState.value.copy(
                                progress = processed.toFloat() / totalFiles,
                                status = "正在压缩加密文件... ($processed/$totalFiles)"
                            )
                        }
                    }
                    
                    _backupState.value = _backupState.value.copy(progress = 0.8f, status = "正在保存到Downloads...")
                    
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
                    
                    _restoreState.value = _restoreState.value.copy(progress = 0.3f, status = "正在解压...")
                    
                    val filesDir = context.filesDir
                    val encryptedDir = File(filesDir, "encrypted_files")
                    val databaseDir = File(context.filesDir, "database")
                    
                    databaseDir.mkdirs()
                    encryptedDir.mkdirs()
                    
                    ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                        var entry = zipIn.nextEntry
                        var totalEntries = 0
                        val entries = mutableListOf<Pair<String, String>>()
                        
                        while (entry != null) {
                            entries.add(entry.name to entry.name.substringBefore("/"))
                            totalEntries++
                            entry = zipIn.nextEntry
                        }
                        
                        var processed = 0
                        zipIn.close()
                        
                        ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                            entry = zipIn.nextEntry
                            while (entry != null) {
                                val targetPath = when {
                                    entry.name.startsWith("database/") -> 
                                        File(databaseDir, entry.name.substringAfter("database/"))
                                    entry.name.startsWith("encrypted/") -> 
                                        File(encryptedDir, entry.name.substringAfter("encrypted/"))
                                    else -> null
                                }
                                
                                targetPath?.let { target ->
                                    target.parentFile?.mkdirs()
                                    FileOutputStream(target).use { output ->
                                        zipIn.copyTo(output)
                                    }
                                }
                                
                                processed++
                                _restoreState.value = _restoreState.value.copy(
                                    progress = 0.3f + (processed.toFloat() / totalEntries) * 0.6f,
                                    status = "正在恢复... ($processed/$totalEntries)"
                                )
                                
                                entry = zipIn.nextEntry
                            }
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

    private fun saveToDownloads(file: File, mimeType: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
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
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    context: Context = LocalContext.current,
    database: FileSafeDatabase = remember { FileSafeDatabase.getInstance(context) },
    cryptoManager: CryptoManager = remember { CryptoManager(context) }
) {
    val viewModel = remember {
        BackupViewModel(context, database, cryptoManager)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val backupState by viewModel.backupState.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    
    var showPasswordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var usePassword by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isRestorePassword by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    viewModel.restoreBackup(
                        inputStream = inputStream,
                        password = if (isRestorePassword) restorePassword else null,
                        onSuccess = {
                            scope.launch {
                                snackbarHostState.showSnackbar("恢复成功，请重启应用")
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
                        text = "将所有加密文件和数据库打包保存到Downloads目录",
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
                            onClick = {
                                if (usePassword) {
                                    showPasswordDialog = true
                                } else {
                                    viewModel.createBackup(
                                        password = null,
                                        onSuccess = { fileName ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("备份已保存: $fileName")
                                            }
                                        },
                                        onError = { error ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("备份失败: $error")
                                            }
                                        }
                                    )
                                }
                            },
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
                        text = "从备份文件恢复您的数据和加密文件",
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
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
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
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
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
                        text = "恢复备份将覆盖当前所有数据，此操作不可撤销。请在恢复前确保已备份当前数据。",
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
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
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
}
