package cn.logicliu.filesafe.ui.screens.home

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.logicliu.filesafe.data.FileDataStore
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.repository.FileRepository
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.ui.components.CreateFolderDialog
import cn.logicliu.filesafe.ui.components.EmptyState
import cn.logicliu.filesafe.ui.components.FileListItem
import cn.logicliu.filesafe.ui.components.FileOperation
import cn.logicliu.filesafe.ui.components.FileOperationDialog
import cn.logicliu.filesafe.ui.components.FolderItem
import cn.logicliu.filesafe.ui.components.FolderOperationDialog
import cn.logicliu.filesafe.ui.viewmodel.FileViewModel
import cn.logicliu.filesafe.ui.viewmodel.ViewMode

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
    val fileRepository = remember { FileRepository(context, fileDataStore, cryptoManager) }
    val viewModel = remember { FileViewModel(fileRepository) }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val currentFiles by viewModel.currentFiles.collectAsState()
    val currentFolders by viewModel.currentFolders.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<Any?>(null) }
    var showOperationDialog by remember { mutableStateOf<FileOperation?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.importFile(uri)
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

    val allFolders = currentFolders

    Scaffold(
        topBar = {
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
                                    selectedItem = "folder"
                                    showOperationDialog = FileOperation.DELETE
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (currentFolders.isEmpty() && currentFiles.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.FolderOff,
                title = "文件夹为空",
                subtitle = "点击右下角按钮添加文件",
                modifier = Modifier.padding(paddingValues)
            )
        } else {
            if (viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                        FolderItem(
                            folder = folder,
                            isGridView = false,
                            onClick = { onNavigateToSubFolder(folder.id, folder.name) },
                            onLongClick = {
                                selectedItem = folder
                                showOperationDialog = FileOperation.DELETE
                            }
                        )
                    }
                    items(currentFiles, key = { "file_${it.id}" }) { file ->
                        FileListItem(
                            file = file,
                            isGridView = false,
                            onClick = { },
                            onLongClick = {
                                selectedItem = file
                                showOperationDialog = FileOperation.DELETE
                            }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(currentFolders, key = { "folder_${it.id}" }) { folder ->
                        FolderItem(
                            folder = folder,
                            isGridView = true,
                            onClick = { onNavigateToSubFolder(folder.id, folder.name) },
                            onLongClick = {
                                selectedItem = folder
                                showOperationDialog = FileOperation.DELETE
                            }
                        )
                    }
                    items(currentFiles, key = { "file_${it.id}" }) { file ->
                        FileListItem(
                            file = file,
                            isGridView = true,
                            onClick = { },
                            onLongClick = {
                                selectedItem = file
                                showOperationDialog = FileOperation.DELETE
                            }
                        )
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

    selectedItem?.let { item ->
        showOperationDialog?.let { operation ->
            when (item) {
                is FileItemEntity -> {
                    FileOperationDialog(
                        file = item,
                        folders = allFolders,
                        operation = operation,
                        onDismiss = {
                            selectedItem = null
                            showOperationDialog = null
                        },
                        onConfirm = { value ->
                            when (operation) {
                                FileOperation.RENAME -> {
                                    value?.let { viewModel.renameFile(item.id, it) }
                                }
                                FileOperation.DELETE -> {
                                    viewModel.deleteFile(item.id)
                                }
                                FileOperation.MOVE -> {
                                    value?.toLongOrNull()?.let { targetId ->
                                        viewModel.moveFile(item.id, targetId)
                                    }
                                }
                                else -> {}
                            }
                            selectedItem = null
                            showOperationDialog = null
                        }
                    )
                }
                is FolderEntity -> {
                    FolderOperationDialog(
                        folder = item,
                        folders = allFolders,
                        operation = operation,
                        onDismiss = {
                            selectedItem = null
                            showOperationDialog = null
                        },
                        onConfirm = { value ->
                            when (operation) {
                                FileOperation.RENAME -> {
                                    value?.let { viewModel.renameFolder(item.id, it) }
                                }
                                FileOperation.DELETE -> {
                                    viewModel.deleteFolder(item.id)
                                }
                                else -> {}
                            }
                            selectedItem = null
                            showOperationDialog = null
                        }
                    )
                }
                "folder" -> {
                    FolderOperationDialog(
                        folder = FolderEntity(id = folderId, name = folderName, path = "", encryptedPath = "", parentId = null, createdAt = 0, modifiedAt = 0),
                        folders = allFolders,
                        operation = operation,
                        onDismiss = {
                            selectedItem = null
                            showOperationDialog = null
                        },
                        onConfirm = { _ ->
                            selectedItem = null
                            showOperationDialog = null
                            onNavigateBack()
                        }
                    )
                }
            }
        }
    }
}
