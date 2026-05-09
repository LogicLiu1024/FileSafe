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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.data.FileDataStore
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.repository.FileRepository
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.service.EncryptionService
import cn.logicliu.filesafe.service.ExportTempFileHolder
import cn.logicliu.filesafe.service.FileTaskOperation
import cn.logicliu.filesafe.ui.components.CreateFolderDialog
import cn.logicliu.filesafe.ui.components.EmptyState
import cn.logicliu.filesafe.ui.components.FileListItem
import cn.logicliu.filesafe.ui.components.FileOperation as UiFileOperation
import cn.logicliu.filesafe.ui.components.FileOperationDialog
import cn.logicliu.filesafe.ui.components.FolderItem
import cn.logicliu.filesafe.ui.components.FolderOperationDialog
import cn.logicliu.filesafe.ui.components.FolderOperationMenu
import cn.logicliu.filesafe.ui.components.FileOperationMenu
import cn.logicliu.filesafe.ui.screens.viewer.FileViewerScreen
import cn.logicliu.filesafe.ui.viewmodel.FileCategory
import cn.logicliu.filesafe.ui.viewmodel.FileViewModel
import cn.logicliu.filesafe.ui.viewmodel.ViewMode
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTrash: () -> Unit,
    onNavigateToFolder: (Long, String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val fileDataStore = remember { FileDataStore.getInstance(context) }
    val cryptoManager = remember { CryptoManager(context) }
    val fileRepository = remember { FileRepository(context, fileDataStore, cryptoManager) }
    val viewModel = remember { FileViewModel(fileRepository, context) }
    val scope = rememberCoroutineScope()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val combinedContents by viewModel.combinedContents.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    val selectedFileForView by viewModel.selectedFileForView.collectAsState()
    val fileOperationProgress by viewModel.fileOperationProgress.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<Any?>(null) }
    var showFileOperationMenu by remember { mutableStateOf(false) }
    var showFolderOperationMenu by remember { mutableStateOf(false) }
    var showOperationDialog by remember { mutableStateOf<UiFileOperation?>(null) }
    var targetOperationFolderId by remember { mutableStateOf<Long?>(null) }

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
            ExportTempFileHolder.tempFile?.let { tempFile ->
                copyFile(context, Uri.fromFile(tempFile), it)
                viewModel.clearOperationProgress()
            }
        }
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
        if (fileOperationProgress?.operation == FileTaskOperation.EXPORT && 
            fileOperationProgress?.result == cn.logicliu.filesafe.service.OperationResult.SUCCESS) {
            ExportTempFileHolder.tempFile?.let { file ->
                saveFileLauncher.launch(file.name)
            }
        }
    }

    val filteredContents = remember(combinedContents, selectedCategory) {
        viewModel.filterByCategory(combinedContents, selectedCategory)
    }

    val folders = filteredContents.filterIsInstance<FolderEntity>()
    val files = filteredContents.filterIsInstance<FileItemEntity>()

    if (selectedFileForView != null) {
        FileViewerScreen(
            file = selectedFileForView!!,
            onNavigateBack = { viewModel.clearSelectedFileForView() }
        )
        return
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(2f / 3f)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "文件保险箱",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Image, contentDescription = null) },
                    label = { Text("全部文件") },
                    selected = true,
                    onClick = {
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text("回收站") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToTrash()
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text("设置") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (currentFolderId != null) currentFolderName else "文件保险箱",
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "菜单")
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
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
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
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // File operation progress indicator
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
                                    text = if (progress.operation == FileTaskOperation.IMPORT) "导入文件" else "导出文件",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (progress.isRunning) {
                                    IconButton(onClick = {
                                        EncryptionService.cancelOperation(context)
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
                                    overflow = TextOverflow.Ellipsis
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

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == FileCategory.ALL,
                            onClick = { viewModel.setCategory(FileCategory.ALL) },
                            label = { Text("全部") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == FileCategory.IMAGE,
                            onClick = { viewModel.setCategory(FileCategory.IMAGE) },
                            label = { Text("图片") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == FileCategory.VIDEO,
                            onClick = { viewModel.setCategory(FileCategory.VIDEO) },
                            label = { Text("视频") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.VideoFile,
                                    contentDescription = null,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == FileCategory.DOCUMENT,
                            onClick = { viewModel.setCategory(FileCategory.DOCUMENT) },
                            label = { Text("文档") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedCategory == FileCategory.OTHER,
                            onClick = { viewModel.setCategory(FileCategory.OTHER) },
                            label = { Text("其他") }
                        )
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (filteredContents.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = "暂无文件",
                        subtitle = "点击右下角按钮添加文件"
                    )
                } else {
                    if (viewMode == ViewMode.LIST) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(folders, key = { "folder_${it.id}" }) { folder ->
                                FolderItem(
                                    folder = folder,
                                    isGridView = false,
                                    onClick = { onNavigateToFolder(folder.id, folder.name) },
                                    onLongClick = {
                                        selectedItem = folder
                                        showFolderOperationMenu = true
                                    }
                                )
                            }
                            items(files, key = { "file_${it.id}" }) { file ->
                                FileListItem(
                                    file = file,
                                    isGridView = false,
                                    onClick = { viewModel.viewFile(file.id) },
                                    onLongClick = {
                                        selectedItem = file
                                        showFileOperationMenu = true
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
                            items(folders, key = { "folder_${it.id}" }) { folder ->
                                FolderItem(
                                    folder = folder,
                                    isGridView = true,
                                    onClick = { onNavigateToFolder(folder.id, folder.name) },
                                    onLongClick = {
                                        selectedItem = folder
                                        showFolderOperationMenu = true
                                    }
                                )
                            }
                            items(files, key = { "file_${it.id}" }) { file ->
                                FileListItem(
                                    file = file,
                                    isGridView = true,
                                    onClick = { viewModel.viewFile(file.id) },
                                    onLongClick = {
                                        selectedItem = file
                                        showFileOperationMenu = true
                                    }
                                )
                            }
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

    if (showFileOperationMenu && selectedItem is FileItemEntity) {
        FileOperationMenu(
            onDismiss = { 
                showFileOperationMenu = false
                selectedItem = null
            },
            onView = {
                viewModel.viewFile((selectedItem as FileItemEntity).id)
            },
            onCopy = {
                showOperationDialog = UiFileOperation.MOVE
                targetOperationFolderId = null
            },
            onMove = {
                showOperationDialog = UiFileOperation.MOVE
                targetOperationFolderId = (selectedItem as FileItemEntity).id
            },
            onRename = {
                showOperationDialog = UiFileOperation.RENAME
            },
            onExport = {
                viewModel.exportFile((selectedItem as FileItemEntity).id)
            },
            onDelete = {
                showOperationDialog = UiFileOperation.DELETE
            }
        )
    }

    if (showFolderOperationMenu && selectedItem is FolderEntity) {
        FolderOperationMenu(
            onDismiss = { 
                showFolderOperationMenu = false
                selectedItem = null
            },
            onCopy = {
                showOperationDialog = UiFileOperation.MOVE
                targetOperationFolderId = null
            },
            onMove = {
                showOperationDialog = UiFileOperation.MOVE
                targetOperationFolderId = (selectedItem as FolderEntity).id
            },
            onRename = {
                showOperationDialog = UiFileOperation.RENAME
            },
            onDelete = {
                showOperationDialog = UiFileOperation.DELETE
            }
        )
    }

    selectedItem?.let { item ->
        showOperationDialog?.let { operation ->
            when (item) {
                is FileItemEntity -> {
                    FileOperationDialog(
                        file = item,
                        folders = folders,
                        operation = operation,
                        onDismiss = {
                            selectedItem = null
                            showOperationDialog = null
                            targetOperationFolderId = null
                        },
                        onConfirm = { value ->
                            when (operation) {
                                UiFileOperation.RENAME -> {
                                    value?.let { viewModel.renameFile(item.id, it) }
                                }
                                UiFileOperation.DELETE -> {
                                    viewModel.deleteFile(item.id)
                                }
                                UiFileOperation.MOVE -> {
                                    if (targetOperationFolderId == null) {
                                        // Copy operation
                                        value?.toLongOrNull()?.let { targetId ->
                                            viewModel.copyFile(item.id, targetId)
                                        } ?: run {
                                            viewModel.copyFile(item.id, null)
                                        }
                                    } else {
                                        // Move operation
                                        value?.toLongOrNull()?.let { targetId ->
                                            viewModel.moveFile(item.id, targetId)
                                        } ?: run {
                                            viewModel.moveFile(item.id, null)
                                        }
                                    }
                                }
                                else -> {}
                            }
                            selectedItem = null
                            showOperationDialog = null
                            targetOperationFolderId = null
                        }
                    )
                }
                is FolderEntity -> {
                    FolderOperationDialog(
                        folder = item,
                        folders = folders.filter { it.id != item.id },
                        operation = operation,
                        onDismiss = {
                            selectedItem = null
                            showOperationDialog = null
                            targetOperationFolderId = null
                        },
                        onConfirm = { value ->
                            when (operation) {
                                UiFileOperation.RENAME -> {
                                    value?.let { viewModel.renameFolder(item.id, it) }
                                }
                                UiFileOperation.DELETE -> {
                                    viewModel.deleteFolder(item.id)
                                }
                                UiFileOperation.MOVE -> {
                                    if (targetOperationFolderId == null) {
                                        // Copy operation
                                        value?.toLongOrNull()?.let { targetId ->
                                            viewModel.copyFolder(item.id, targetId)
                                        } ?: run {
                                            viewModel.copyFolder(item.id, null)
                                        }
                                    } else {
                                        // Move operation
                                        value?.toLongOrNull()?.let { targetId ->
                                            viewModel.moveFolder(item.id, targetId)
                                        } ?: run {
                                            viewModel.moveFolder(item.id, null)
                                        }
                                    }
                                }
                                else -> {}
                            }
                            selectedItem = null
                            showOperationDialog = null
                            targetOperationFolderId = null
                        }
                    )
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
