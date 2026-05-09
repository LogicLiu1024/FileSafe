package cn.logicliu.filesafe.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.entity.TrashItemEntity
import cn.logicliu.filesafe.data.repository.FileRepository
import cn.logicliu.filesafe.service.EncryptionService
import cn.logicliu.filesafe.service.ExportTempFileHolder
import cn.logicliu.filesafe.service.FileTaskOperation
import cn.logicliu.filesafe.service.FileOperationProgress
import cn.logicliu.filesafe.service.OperationResult
import cn.logicliu.filesafe.service.ProgressManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class FileCategory {
    ALL, IMAGE, VIDEO, DOCUMENT, OTHER
}

enum class ViewMode {
    GRID, LIST
}

class FileViewModel(
    private val fileRepository: FileRepository,
    private val context: Context
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow("根目录")
    val currentFolderName: StateFlow<String> = _currentFolderName.asStateFlow()

    val currentFiles: MutableStateFlow<List<FileItemEntity>> = MutableStateFlow(emptyList())

    val currentFolders: MutableStateFlow<List<FolderEntity>> = MutableStateFlow(emptyList())

    val trashItems: StateFlow<List<TrashItemEntity>> = fileRepository.getAllTrashItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationSuccess = MutableStateFlow<String?>(null)
    val operationSuccess: StateFlow<String?> = _operationSuccess.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Any>>(emptyList())
    val searchResults: StateFlow<List<Any>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _fileOperationProgress = MutableStateFlow<FileOperationProgress?>(null)
    val fileOperationProgress: StateFlow<FileOperationProgress?> = _fileOperationProgress.asStateFlow()

    val combinedContents: StateFlow<List<Any>> = fileRepository.getCombinedContents(null)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val progressListener: (FileOperationProgress) -> Unit = { progress ->
        _fileOperationProgress.value = progress
        
        when (progress.result) {
            OperationResult.SUCCESS -> {
                _operationSuccess.value = when (progress.operation) {
                    FileTaskOperation.IMPORT -> "文件导入成功"
                    FileTaskOperation.EXPORT -> "文件导出成功"
                }
            }
            OperationResult.ERROR -> {
                _error.value = when (progress.operation) {
                    FileTaskOperation.IMPORT -> "导入失败: ${progress.errorMessage}"
                    FileTaskOperation.EXPORT -> "导出失败: ${progress.errorMessage}"
                }
            }
            OperationResult.CANCELLED -> {
                _operationSuccess.value = "操作已取消"
            }
            null -> {}
        }
    }

    init {
        ProgressManager.addListener(progressListener)
    }

    override fun onCleared() {
        super.onCleared()
        ProgressManager.removeListener(progressListener)
    }

    fun navigateToFolder(folderId: Long, folderName: String) {
        _currentFolderId.value = folderId
        _currentFolderName.value = folderName
        viewModelScope.launch {
            fileRepository.getFilesByFolder(folderId).collect { files ->
                currentFiles.value = files
            }
        }
        viewModelScope.launch {
            fileRepository.getFoldersByParent(folderId).collect { folders ->
                currentFolders.value = folders
            }
        }
    }

    fun navigateBack(): Boolean {
        return if (_currentFolderId.value != null) {
            _currentFolderId.value = null
            _currentFolderName.value = "根目录"
            true
        } else {
            false
        }
    }

    fun navigateToRoot() {
        _currentFolderId.value = null
        _currentFolderName.value = "根目录"
    }

    fun setCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }

    fun importFile(uri: Uri) {
        _error.value = null
        _fileOperationProgress.value = FileOperationProgress(
            isRunning = true,
            progress = 0f,
            operation = FileTaskOperation.IMPORT
        )
        
        // Create temp file path
        val tempDir = File(context.cacheDir, "temp").apply { mkdirs() }
        val tempFilePath = File(tempDir, "temp_import_${UUID.randomUUID()}").absolutePath
        
        EncryptionService.startImport(context, uri, tempFilePath, _currentFolderId.value)
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.createFolder(name, _currentFolderId.value)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件夹创建成功"
                },
                onFailure = { e ->
                    _error.value = "创建文件夹失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun deleteFile(fileId: Long, moveToTrash: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.deleteFile(fileId, moveToTrash)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = if (moveToTrash) "文件已移到回收站" else "文件已删除"
                },
                onFailure = { e ->
                    _error.value = "删除文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun deleteFolder(folderId: Long, moveToTrash: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.deleteFolder(folderId, moveToTrash)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = if (moveToTrash) "文件夹已移到回收站" else "文件夹已删除"
                },
                onFailure = { e ->
                    _error.value = "删除文件夹失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun renameFile(fileId: Long, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.renameFile(fileId, newName)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件重命名成功"
                },
                onFailure = { e ->
                    _error.value = "重命名文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.renameFolder(folderId, newName)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件夹重命名成功"
                },
                onFailure = { e ->
                    _error.value = "重命名文件夹失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun moveFile(fileId: Long, targetFolderId: Long?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.moveFile(fileId, targetFolderId)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件移动成功"
                },
                onFailure = { e ->
                    _error.value = "移动文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun copyFile(fileId: Long, targetFolderId: Long?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.copyFile(fileId, targetFolderId)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件复制成功"
                },
                onFailure = { e ->
                    _error.value = "复制文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun copyFolder(folderId: Long, targetParentFolderId: Long?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.copyFolder(folderId, targetParentFolderId)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件夹复制成功"
                },
                onFailure = { e ->
                    _error.value = "复制文件夹失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun moveFolder(folderId: Long, targetParentFolderId: Long?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.moveFolder(folderId, targetParentFolderId)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件夹移动成功"
                },
                onFailure = { e ->
                    _error.value = "移动文件夹失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    private val _selectedFileForView = MutableStateFlow<File?>(null)
    val selectedFileForView: StateFlow<File?> = _selectedFileForView.asStateFlow()

    fun viewFile(fileId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.getDecryptedFile(fileId)
            result.fold(
                onSuccess = { file ->
                    _selectedFileForView.value = file
                },
                onFailure = { e ->
                    _error.value = "打开文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun clearSelectedFileForView() {
        _selectedFileForView.value = null
    }

    fun exportFile(fileId: Long) {
        _error.value = null
        _fileOperationProgress.value = FileOperationProgress(
            isRunning = true,
            progress = 0f,
            operation = FileTaskOperation.EXPORT
        )
        EncryptionService.startExport(context, fileId)
    }

    fun clearOperationProgress() {
        _fileOperationProgress.value = null
        ExportTempFileHolder.tempFile = null
    }

    fun restoreFromTrash(trashItemId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.restoreFromTrash(trashItemId)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件已恢复"
                },
                onFailure = { e ->
                    _error.value = "恢复文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun permanentlyDeleteTrashItem(trashItemId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.permanentlyDeleteTrashItem(trashItemId)
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "文件已永久删除"
                },
                onFailure = { e ->
                    _error.value = "删除文件失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = fileRepository.emptyTrash()
            result.fold(
                onSuccess = {
                    _operationSuccess.value = "回收站已清空"
                },
                onFailure = { e ->
                    _error.value = "清空回收站失败: ${e.message}"
                }
            )
            _isLoading.value = false
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true
            fileRepository.searchFiles(query).collect { files ->
                fileRepository.searchFolders(query).collect { folders ->
                    _searchResults.value = (folders + files).sortedBy {
                        when (it) {
                            is FolderEntity -> "0_${it.name.lowercase()}"
                            else -> "1_${(it as FileItemEntity).name.lowercase()}"
                        }
                    }
                }
            }
            _isSearching.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _operationSuccess.value = null
    }

    fun filterByCategory(items: List<Any>, category: FileCategory): List<Any> {
        if (category == FileCategory.ALL) return items

        return items.filter { item ->
            when (item) {
                is FolderEntity -> true
                is FileItemEntity -> {
                    val extension = item.name.substringAfterLast('.', "").lowercase()
                    when (category) {
                        FileCategory.IMAGE -> extension in IMAGE_EXTENSIONS
                        FileCategory.VIDEO -> extension in VIDEO_EXTENSIONS
                        FileCategory.DOCUMENT -> extension in DOCUMENT_EXTENSIONS
                        FileCategory.OTHER -> extension !in IMAGE_EXTENSIONS &&
                                extension !in VIDEO_EXTENSIONS &&
                                extension !in DOCUMENT_EXTENSIONS
                        FileCategory.ALL -> true
                    }
                }
                else -> false
            }
        }
    }

    companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv")
        val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
    }
}
