package cn.logicliu.filesafe.ui.screens.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.data.FileDataStore
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.repository.FileRepository
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.security.SecuritySettingsManager
import cn.logicliu.filesafe.ui.components.CreateFolderDialog
import cn.logicliu.filesafe.ui.components.EmptyState
import cn.logicliu.filesafe.ui.components.FileListItem
import cn.logicliu.filesafe.ui.components.FolderItem
import cn.logicliu.filesafe.ui.components.MultiSelectBottomBar
import cn.logicliu.filesafe.service.ExportTempFileHolder
import cn.logicliu.filesafe.ui.screens.viewer.FileViewerScreen
import cn.logicliu.filesafe.ui.viewmodel.FileViewModel
import cn.logicliu.filesafe.ui.viewmodel.ViewMode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderId: Long,
    folderName: String,
    onNavigateBack: () -> Unit,
    onNavigateToSubFolder: (Long, String) -> Unit
) {
    val context = LocalContext.current
    val fileDataStore = remember { FileDataStore.getInstance(context) }
    val cryptoManager = remember { CryptoManager(context) }
    val securitySettingsManager = remember { SecuritySettingsManager(context) }
    val fileRepository = remember { FileRepository(context, fileDataStore, cryptoManager, securitySettingsManager) }
    val viewModel = remember { FileViewModel(fileRepository, context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val currentFiles by viewModel.currentFiles.collectAsState()
    val currentFolders by viewModel.currentFolders.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    val selectedFileForView by viewModel.selectedFileForView.collectAsState()
    val fileOperationProgress by viewModel.fileOperationProgress.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    var selectedFileIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedFolderIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    var showBatchMoveDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }
    var showDeleteCurrentFolderDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedFileIds = emptySet()
        selectedFolderIds = emptySet()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importFile(uri)
            }
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let {
            ExportTempFileHolder.tempFile?.let { sourceFile ->
                copyFile(context, Uri.fromFile(sourceFile), it)
                viewModel.clearOperationProgress()
            }
        }
    }

    LaunchedEffect(folderId, folderName) {
        viewModel.navigateToFolder(folderId, folderName)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(operationSuccess) {
        operationSuccess?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    LaunchedEffect(fileOperationProgress?.result) {
        if (fileOperationProgress?.operation == cn.logicliu.filesafe.service.FileTaskOperation.EXPORT && 
            fileOperationProgress?.result == cn.logicliu.filesafe.service.OperationResult.SUCCESS) {
            ExportTempFileHolder.tempFile?.let { file ->
                saveFileLauncher.launch(file.name)
            }
        }
        if (fileOperationProgress?.operation == cn.logicliu.filesafe.service.FileTaskOperation.IMPORT && 
            fileOperationProgress?.result != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearOperationProgress()
        }
    }

    val allFolders = currentFolders

    if (selectedFileForView != null) {
        FileViewerScreen(
            file = selectedFileForView!!,
            onNavigateBack = { viewModel.clearSelectedFileForView() }
        )
        return
    }

    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "已选 ${selectedFileIds.size + selectedFolderIds.size} 项",
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(currentFolderName, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.setViewMode(
                                if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                            )
                        }) {
                            Icon(
                                imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.List,
                                contentDescription = "切换视图"
                            )
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("新建文件夹") },
                                    onClick = {
                                        showMoreMenu = false
                                        showCreateFolderDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除文件夹") },
                                    onClick = {
                                        showMoreMenu = false
                                        showDeleteCurrentFolderDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null)
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        filePickerLauncher.launch(intent)
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加文件")
                }
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                MultiSelectBottomBar(
                    selectedCount = selectedFileIds.size + selectedFolderIds.size,
                    onlyFilesSelected = selectedFolderIds.isEmpty(),
                    onCancel = { exitSelectionMode() },
                    onSelectAll = {
                        selectedFileIds = currentFiles.map { it.id }.toSet()
                        selectedFolderIds = currentFolders.map { it.id }.toSet()
                    },
                    onMove = {
                        if (selectedFileIds.size + selectedFolderIds.size > 0) {
                            showBatchMoveDialog = true
                        }
                    },
                    onRename = {
                        if (selectedFileIds.size == 1 && selectedFolderIds.isEmpty()) {
                            val fileId = selectedFileIds.first()
                            val file = currentFiles.find { it.id == fileId }
                            if (file != null) {
                                showBatchRenameDialog = true
                            }
                        }
                    },
                    onExport = {
                        if (selectedFileIds.size == 1 && selectedFolderIds.isEmpty()) {
                            viewModel.exportFile(selectedFileIds.first())
                            exitSelectionMode()
                        }
                    },
                    onDelete = {
                        if (selectedFileIds.size + selectedFolderIds.size > 0) {
                            showBatchDeleteDialog = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            fileOperationProgress?.let { progress ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (progress.operation == cn.logicliu.filesafe.service.FileTaskOperation.IMPORT) "导入文件" else "导出文件",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (progress.isRunning) {
                                IconButton(onClick = {
                                    cn.logicliu.filesafe.service.EncryptionService.cancelOperation(context)
                                }) {
                                    Icon(Icons.Default.Cancel, contentDescription = "取消")
                                }
                            }
                        }
                        if (progress.fileName.isNotEmpty()) {
                            Text(
                                text = progress.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(progress.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (currentFolders.isEmpty() && currentFiles.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.FolderOff,
                    title = "文件夹为空",
                    subtitle = "点击右下角按钮添加文件"
                )
            } else {
                if (viewMode == ViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                            FolderItem(
                                folder = folder,
                                isGridView = false,
                                isSelected = folder.id in selectedFolderIds,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFolderIds = if (folder.id in selectedFolderIds) {
                                            selectedFolderIds - folder.id
                                        } else {
                                            selectedFolderIds + folder.id
                                        }
                                        if (selectedFileIds.isEmpty() && selectedFolderIds.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        onNavigateToSubFolder(folder.id, folder.name)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedFolderIds = setOf(folder.id)
                                }
                            )
                        }
                        items(currentFiles, key = { "file_${it.id}" }) { file ->
                            FileListItem(
                                file = file,
                                isGridView = false,
                                isSelected = file.id in selectedFileIds,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFileIds = if (file.id in selectedFileIds) {
                                            selectedFileIds - file.id
                                        } else {
                                            selectedFileIds + file.id
                                        }
                                        if (selectedFileIds.isEmpty() && selectedFolderIds.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        viewModel.viewFile(file.id)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedFileIds = setOf(file.id)
                                }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                            FolderItem(
                                folder = folder,
                                isGridView = true,
                                isSelected = folder.id in selectedFolderIds,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFolderIds = if (folder.id in selectedFolderIds) {
                                            selectedFolderIds - folder.id
                                        } else {
                                            selectedFolderIds + folder.id
                                        }
                                        if (selectedFileIds.isEmpty() && selectedFolderIds.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        onNavigateToSubFolder(folder.id, folder.name)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedFolderIds = setOf(folder.id)
                                }
                            )
                        }
                        items(currentFiles, key = { "file_${it.id}" }) { file ->
                            FileListItem(
                                file = file,
                                isGridView = true,
                                isSelected = file.id in selectedFileIds,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedFileIds = if (file.id in selectedFileIds) {
                                            selectedFileIds - file.id
                                        } else {
                                            selectedFileIds + file.id
                                        }
                                        if (selectedFileIds.isEmpty() && selectedFolderIds.isEmpty()) {
                                            isSelectionMode = false
                                        }
                                    } else {
                                        viewModel.viewFile(file.id)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedFileIds = setOf(file.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                viewModel.createFolder(folderName)
                showCreateFolderDialog = false
            }
        )
    }

    if (showDeleteCurrentFolderDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCurrentFolderDialog = false },
            title = { Text("删除文件夹") },
            text = { Text("确定要将 \"$folderName\" 移到回收站吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteFolder(folderId)
                    showDeleteCurrentFolderDialog = false
                    onNavigateBack()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCurrentFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatchMoveDialog) {
        BatchMoveSheet(
            folders = allFolders.filter { it.id !in selectedFolderIds },
            onDismiss = { showBatchMoveDialog = false },
            onConfirm = { targetFolderId ->
                if (selectedFileIds.isNotEmpty()) {
                    viewModel.batchMoveFiles(selectedFileIds.toList(), targetFolderId)
                }
                if (selectedFolderIds.isNotEmpty()) {
                    viewModel.batchMoveFolders(selectedFolderIds.toList(), targetFolderId)
                }
                showBatchMoveDialog = false
                exitSelectionMode()
            }
        )
    }

    if (showBatchDeleteDialog) {
        val totalCount = selectedFileIds.size + selectedFolderIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("批量删除") },
            text = { Text("确定要将选中的 $totalCount 个项目移到回收站吗？") },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedFileIds.isNotEmpty()) {
                        viewModel.batchDeleteFiles(selectedFileIds.toList())
                    }
                    if (selectedFolderIds.isNotEmpty()) {
                        viewModel.batchDeleteFolders(selectedFolderIds.toList())
                    }
                    showBatchDeleteDialog = false
                    exitSelectionMode()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatchRenameDialog) {
        val fileId = selectedFileIds.first()
        val file = currentFiles.find { it.id == fileId }
        var newName by remember(file) { mutableStateOf(file?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showBatchRenameDialog = false },
            title = { Text("重命名文件") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新文件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameFile(fileId, newName)
                        showBatchRenameDialog = false
                        exitSelectionMode()
                    },
                    enabled = newName.isNotBlank() && newName != file?.name
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchRenameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchMoveSheet(
    folders: List<FolderEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    var selectedFolderId by remember { mutableStateOf<Long?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "移动到",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            ListItem(
                headlineContent = { Text("根目录") },
                leadingContent = {
                    Icon(Icons.Default.Folder, contentDescription = null)
                },
                modifier = Modifier.selectable(
                    selected = selectedFolderId == null,
                    onClick = { selectedFolderId = null },
                    role = Role.RadioButton
                )
            )

            folders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    leadingContent = {
                        Icon(Icons.Default.Folder, contentDescription = null)
                    },
                    modifier = Modifier.selectable(
                        selected = selectedFolderId == folder.id,
                        onClick = { selectedFolderId = folder.id },
                        role = Role.RadioButton
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onConfirm(selectedFolderId) }
                ) {
                    Text("移动")
                }
            }
        }
    }
}

private fun copyFile(context: Context, sourceUri: Uri, destinationUri: Uri) {
    context.contentResolver.openInputStream(sourceUri)?.use { input: java.io.InputStream ->
        context.contentResolver.openOutputStream(destinationUri)?.use { output: java.io.OutputStream ->
            input.copyTo(output)
        }
    }
}