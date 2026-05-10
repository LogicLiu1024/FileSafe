package cn.logicliu.filesafe.data.repository

import android.content.Context
import android.net.Uri
import cn.logicliu.filesafe.data.FileDataStore
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.entity.TrashItemEntity
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.security.EncryptionMode
import cn.logicliu.filesafe.security.SecuritySettingsManager
import cn.logicliu.filesafe.service.ThumbnailManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileRepository(
    private val context: Context,
    private val fileDataStore: FileDataStore,
    private val cryptoManager: CryptoManager,
    private val securitySettingsManager: SecuritySettingsManager
) {
    private val encryptedDir: File
        get() = File(context.filesDir, ENCRYPTED_DIR).apply { mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }

    fun getRootFiles(): Flow<List<FileItemEntity>> = fileDataStore.getRootFiles()

    fun getRootFolders(): Flow<List<FolderEntity>> = fileDataStore.getRootFolders()

    fun getFilesByFolder(folderId: Long): Flow<List<FileItemEntity>> =
        fileDataStore.getFilesByFolder(folderId)

    fun getFoldersByParent(parentId: Long): Flow<List<FolderEntity>> =
        fileDataStore.getFoldersByParent(parentId)

    fun getAllTrashItems(): Flow<List<TrashItemEntity>> = fileDataStore.trashItems

    fun searchFiles(query: String): Flow<List<FileItemEntity>> = fileDataStore.searchFiles(query)

    fun searchFolders(query: String): Flow<List<FolderEntity>> = fileDataStore.searchFolders(query)

    fun searchTrashItems(query: String): Flow<List<TrashItemEntity>> =
        fileDataStore.searchTrashItems(query)

    suspend fun importFile(uri: Uri, folderId: Long? = null): Result<FileItemEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Cannot open file"))

                val fileName = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
                val mimeType = context.contentResolver.getType(uri)

                val tempFile = File(tempDir, fileName)
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val encryptionMode = securitySettingsManager.encryptionMode.first()
                val (finalFile, isEncrypted) = if (encryptionMode == EncryptionMode.ENCRYPT) {
                    val encryptedFileName = "${UUID.randomUUID()}.enc"
                    val encryptedFile = File(encryptedDir, encryptedFileName)

                    val encryptSuccess = cryptoManager.encryptFile(tempFile, encryptedFile)
                    tempFile.delete()

                    if (!encryptSuccess) {
                        return@withContext Result.failure(Exception("Encryption failed"))
                    }
                    
                    Pair(encryptedFile, true)
                } else {
                    val hiddenFileName = ".${UUID.randomUUID()}"
                    val hiddenFile = File(encryptedDir, hiddenFileName)
                    
                    tempFile.copyTo(hiddenFile, overwrite = true)
                    tempFile.delete()
                    
                    Pair(hiddenFile, false)
                }

                val currentTime = System.currentTimeMillis()
                val fileEntity = FileItemEntity(
                    name = fileName,
                    path = fileName,
                    encryptedPath = finalFile.absolutePath,
                    size = finalFile.length(),
                    mimeType = mimeType,
                    folderId = folderId,
                    createdAt = currentTime,
                    modifiedAt = currentTime,
                    isEncrypted = isEncrypted
                )

                val id = fileDataStore.insertFile(fileEntity)
                val savedEntity = fileEntity.copy(id = id)

                if (ThumbnailManager.isThumbnailSupported(fileName)) {
                    if (isEncrypted) {
                        ThumbnailManager.generateFromEncryptedFile(
                            context, cryptoManager, finalFile, id, mimeType
                        )
                    } else {
                        ThumbnailManager.generateFromPlainFile(
                            context, finalFile, id, mimeType
                        )
                    }
                }

                Result.success(savedEntity)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun exportFile(fileId: Long): Result<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                val fileEntity = fileDataStore.getFileById(fileId).first()
                    ?: return@withContext Result.failure(Exception("File not found"))

                val encryptedFile = File(fileEntity.encryptedPath)
                if (!encryptedFile.exists()) {
                    return@withContext Result.failure(Exception("Encrypted file not found"))
                }

                val decryptedFile = File(tempDir, fileEntity.name)
                
                if (fileEntity.isEncrypted) {
                    val decryptSuccess = cryptoManager.decryptFile(encryptedFile, decryptedFile)

                    if (!decryptSuccess) {
                        return@withContext Result.failure(Exception("Decryption failed"))
                    }
                } else {
                    encryptedFile.copyTo(decryptedFile, overwrite = true)
                }

                val uri = Uri.fromFile(decryptedFile)
                Result.success(uri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDecryptedFile(fileId: Long): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val fileEntity = fileDataStore.getFileById(fileId).first()
                    ?: return@withContext Result.failure(Exception("File not found"))

                val encryptedFile = File(fileEntity.encryptedPath)
                if (!encryptedFile.exists()) {
                    return@withContext Result.failure(Exception("Encrypted file not found"))
                }

                val decryptedFile = File(tempDir, fileEntity.name)
                
                if (fileEntity.isEncrypted) {
                    val decryptSuccess = cryptoManager.decryptFile(encryptedFile, decryptedFile)

                    if (!decryptSuccess) {
                        return@withContext Result.failure(Exception("Decryption failed"))
                    }
                } else {
                    encryptedFile.copyTo(decryptedFile, overwrite = true)
                }

                Result.success(decryptedFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun copyFile(fileId: Long, targetFolderId: Long?): Result<FileItemEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val originalFile = fileDataStore.getFileById(fileId).first()
                    ?: return@withContext Result.failure(Exception("File not found"))

                val encryptedFile = File(originalFile.encryptedPath)
                if (!encryptedFile.exists()) {
                    return@withContext Result.failure(Exception("Encrypted file not found"))
                }

                val newEncryptedFileName = "${UUID.randomUUID()}.enc"
                val newEncryptedFile = File(encryptedDir, newEncryptedFileName)
                encryptedFile.copyTo(newEncryptedFile, overwrite = true)

                val currentTime = System.currentTimeMillis()
                val newFileEntity = FileItemEntity(
                    name = originalFile.name,
                    path = originalFile.path,
                    encryptedPath = newEncryptedFile.absolutePath,
                    size = originalFile.size,
                    mimeType = originalFile.mimeType,
                    folderId = targetFolderId,
                    createdAt = currentTime,
                    modifiedAt = currentTime,
                    isEncrypted = true
                )

                val id = fileDataStore.insertFile(newFileEntity)
                val savedEntity = newFileEntity.copy(id = id)

                if (ThumbnailManager.isThumbnailSupported(originalFile.name)) {
                    val originalThumb = ThumbnailManager.getThumbnailFile(context, fileId)
                    if (originalThumb.exists()) {
                        originalThumb.copyTo(
                            ThumbnailManager.getThumbnailFile(context, id),
                            overwrite = true
                        )
                    } else {
                        ThumbnailManager.generateFromEncryptedFile(
                            context, cryptoManager, newEncryptedFile, id, originalFile.mimeType
                        )
                    }
                }

                Result.success(savedEntity)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun moveFolder(folderId: Long, targetParentFolderId: Long?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val folder = fileDataStore.getFolderById(folderId).first()
                    ?: return@withContext Result.failure(Exception("Folder not found"))

                if (targetParentFolderId != null) {
                    val targetFolder = fileDataStore.getFolderById(targetParentFolderId).first()
                    if (targetFolder == null) {
                        return@withContext Result.failure(Exception("Target folder not found"))
                    }
                    var parent = targetFolder.parentId
                    var isDescendant = false
                    while (parent != null && !isDescendant) {
                        if (parent == folderId) {
                            isDescendant = true
                        } else {
                            val p = fileDataStore.getFolderById(parent).first()
                            parent = p?.parentId
                        }
                    }
                    if (isDescendant) {
                        return@withContext Result.failure(Exception("Cannot move folder into its child"))
                    }
                }

                val updatedFolder = folder.copy(
                    parentId = targetParentFolderId,
                    modifiedAt = System.currentTimeMillis()
                )
                fileDataStore.updateFolder(updatedFolder)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun copyFolder(folderId: Long, targetParentFolderId: Long?): Result<FolderEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val originalFolder = fileDataStore.getFolderById(folderId).first()
                    ?: return@withContext Result.failure(Exception("Folder not found"))

                val newEncryptedFolderPath = File(encryptedDir, UUID.randomUUID().toString()).apply {
                    mkdirs()
                }.absolutePath

                val currentTime = System.currentTimeMillis()
                val newFolder = FolderEntity(
                    name = originalFolder.name,
                    path = "${targetParentFolderId ?: "root"}/${originalFolder.name}",
                    encryptedPath = newEncryptedFolderPath,
                    parentId = targetParentFolderId,
                    createdAt = currentTime,
                    modifiedAt = currentTime,
                    isEncrypted = true
                )

                val newFolderId = fileDataStore.insertFolder(newFolder)

                val files = fileDataStore.getFilesByFolder(folderId).first()
                files.forEach { file ->
                    copyFile(file.id, newFolderId)
                }

                val subfolders = fileDataStore.getFoldersByParent(folderId).first()
                subfolders.forEach { subfolder ->
                    copyFolder(subfolder.id, newFolderId)
                }

                Result.success(newFolder.copy(id = newFolderId))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun createFolder(name: String, parentId: Long? = null): Result<FolderEntity> {
        return withContext(Dispatchers.IO) {
            try {
                val folderPath = "${parentId ?: "root"}/$name"
                val encryptedFolderPath = File(encryptedDir, UUID.randomUUID().toString()).apply {
                    mkdirs()
                }.absolutePath

                val currentTime = System.currentTimeMillis()
                val folderEntity = FolderEntity(
                    name = name,
                    path = folderPath,
                    encryptedPath = encryptedFolderPath,
                    parentId = parentId,
                    createdAt = currentTime,
                    modifiedAt = currentTime,
                    isEncrypted = true
                )

                val id = fileDataStore.insertFolder(folderEntity)
                Result.success(folderEntity.copy(id = id))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteFile(fileId: Long, moveToTrash: Boolean = true): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val fileEntity = fileDataStore.getFileById(fileId).first()
                    ?: return@withContext Result.failure(Exception("File not found"))

                ThumbnailManager.deleteThumbnail(context, fileId)

                if (moveToTrash) {
                    val trashItem = TrashItemEntity(
                        originalName = fileEntity.name,
                        encryptedPath = fileEntity.encryptedPath,
                        originalPath = fileEntity.path,
                        size = fileEntity.size,
                        mimeType = fileEntity.mimeType,
                        deletedAt = System.currentTimeMillis(),
                        itemType = "file",
                        originalFolderId = fileEntity.folderId,
                        isEncrypted = fileEntity.isEncrypted
                    )
                    fileDataStore.insertTrashItem(trashItem)
                } else {
                    File(fileEntity.encryptedPath).delete()
                }

                fileDataStore.deleteFileById(fileId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteFolder(folderId: Long, moveToTrash: Boolean = true): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val folderEntity = fileDataStore.getFolderById(folderId).first()
                    ?: return@withContext Result.failure(Exception("Folder not found"))

                val filesInFolder = fileDataStore.getFilesByFolder(folderId).first()
                filesInFolder.forEach { file ->
                    ThumbnailManager.deleteThumbnail(context, file.id)
                }

                val subfolders = fileDataStore.getFoldersByParent(folderId).first()
                subfolders.forEach { subfolder ->
                    deleteFolderInternal(subfolder.id, moveToTrash)
                }

                if (moveToTrash) {
                    val trashItem = TrashItemEntity(
                        originalName = folderEntity.name,
                        encryptedPath = folderEntity.encryptedPath,
                        originalPath = folderEntity.path,
                        size = 0,
                        mimeType = null,
                        deletedAt = System.currentTimeMillis(),
                        itemType = "folder",
                        originalFolderId = folderEntity.parentId,
                        isEncrypted = folderEntity.isEncrypted
                    )
                    fileDataStore.insertTrashItem(trashItem)
                } else {
                    File(folderEntity.encryptedPath).deleteRecursively()
                }

                fileDataStore.deleteFolderById(folderId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun deleteFolderInternal(folderId: Long, moveToTrash: Boolean) {
        val filesInFolder = fileDataStore.getFilesByFolder(folderId).first()
        filesInFolder.forEach { file ->
            ThumbnailManager.deleteThumbnail(context, file.id)
        }

        val subfolders = fileDataStore.getFoldersByParent(folderId).first()
        subfolders.forEach { subfolder ->
            deleteFolderInternal(subfolder.id, moveToTrash)
        }

        val folderEntity = fileDataStore.getFolderById(folderId).first()
        if (folderEntity != null) {
            if (!moveToTrash) {
                File(folderEntity.encryptedPath).deleteRecursively()
            }
            fileDataStore.deleteFolderById(folderId)
        }
    }

    suspend fun renameFile(fileId: Long, newName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val fileEntity = fileDataStore.getFileById(fileId).first()
                    ?: return@withContext Result.failure(Exception("File not found"))

                val updatedFile = fileEntity.copy(
                    name = newName,
                    path = newName,
                    modifiedAt = System.currentTimeMillis()
                )
                fileDataStore.updateFile(updatedFile)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val folderEntity = fileDataStore.getFolderById(folderId).first()
                    ?: return@withContext Result.failure(Exception("Folder not found"))

                val updatedFolder = folderEntity.copy(
                    name = newName,
                    path = "${folderEntity.parentId ?: "root"}/$newName",
                    modifiedAt = System.currentTimeMillis()
                )
                fileDataStore.updateFolder(updatedFolder)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun moveFile(fileId: Long, targetFolderId: Long?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val fileEntity = fileDataStore.getFileById(fileId).first()
                    ?: return@withContext Result.failure(Exception("File not found"))

                val updatedFile = fileEntity.copy(
                    folderId = targetFolderId,
                    modifiedAt = System.currentTimeMillis()
                )
                fileDataStore.updateFile(updatedFile)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun restoreFromTrash(trashItemId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val trashItem = fileDataStore.getTrashItemById(trashItemId).first()
                    ?: return@withContext Result.failure(Exception("Trash item not found"))

                if (trashItem.itemType == "file") {
                    val fileEntity = FileItemEntity(
                        name = trashItem.originalName,
                        path = trashItem.originalPath,
                        encryptedPath = trashItem.encryptedPath,
                        size = trashItem.size,
                        mimeType = trashItem.mimeType,
                        folderId = trashItem.originalFolderId,
                        createdAt = trashItem.deletedAt,
                        modifiedAt = System.currentTimeMillis(),
                        isEncrypted = trashItem.isEncrypted
                    )
                    val newFileId = fileDataStore.insertFile(fileEntity)

                    if (ThumbnailManager.isThumbnailSupported(trashItem.originalName)) {
                        try {
                            val encryptedFile = File(trashItem.encryptedPath)
                            if (encryptedFile.exists()) {
                                ThumbnailManager.generateFromEncryptedFile(
                                    context, cryptoManager, encryptedFile,
                                    newFileId, trashItem.mimeType
                                )
                            }
                        } catch (_: Exception) { }
                    }
                } else {
                    val folderEntity = FolderEntity(
                        name = trashItem.originalName,
                        path = trashItem.originalPath,
                        encryptedPath = trashItem.encryptedPath,
                        parentId = trashItem.originalFolderId,
                        createdAt = trashItem.deletedAt,
                        modifiedAt = System.currentTimeMillis(),
                        isEncrypted = trashItem.isEncrypted
                    )
                    fileDataStore.insertFolder(folderEntity)
                }

                fileDataStore.deleteTrashItemById(trashItemId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun permanentlyDeleteTrashItem(trashItemId: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val trashItem = fileDataStore.getTrashItemById(trashItemId).first()
                    ?: return@withContext Result.failure(Exception("Trash item not found"))

                if (trashItem.itemType == "folder") {
                    File(trashItem.encryptedPath).deleteRecursively()
                } else {
                    File(trashItem.encryptedPath).delete()
                }

                fileDataStore.deleteTrashItemById(trashItemId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun emptyTrash(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val items = fileDataStore.trashItems.first()
                items.forEach { item ->
                    if (item.itemType == "folder") {
                        File(item.encryptedPath).deleteRecursively()
                    } else {
                        File(item.encryptedPath).delete()
                    }
                }
                fileDataStore.clearTrash()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getCombinedContents(folderId: Long?): Flow<List<Any>> {
        val filesFlow = if (folderId == null) {
            fileDataStore.getRootFiles()
        } else {
            fileDataStore.getFilesByFolder(folderId)
        }

        val foldersFlow = if (folderId == null) {
            fileDataStore.getRootFolders()
        } else {
            fileDataStore.getFoldersByParent(folderId)
        }

        return kotlinx.coroutines.flow.combine(filesFlow, foldersFlow) { files, folders ->
            (folders + files).sortedBy {
                when (it) {
                    is FolderEntity -> "0_${it.name.lowercase()}"
                    else -> "1_${(it as FileItemEntity).name.lowercase()}"
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
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

    suspend fun cleanOrphanedFiles(): Result<OrphanedFilesInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val validFiles = fileDataStore.files.first()
                val validFileIds = validFiles.map { it.id }.toSet()
                val validEncryptedPaths = validFiles.map { it.encryptedPath }.toSet()

                var cleanedEncrypted = 0
                var cleanedEncryptedSize = 0L
                val encryptedDir = File(context.filesDir, ENCRYPTED_DIR)
                if (encryptedDir.exists()) {
                    encryptedDir.walkTopDown().forEach { file ->
                        if (file.isFile && file.absolutePath !in validEncryptedPaths) {
                            cleanedEncryptedSize += file.length()
                            file.delete()
                            cleanedEncrypted++
                        }
                    }
                }

                var cleanedThumbnails = 0
                var cleanedThumbnailsSize = 0L
                val thumbnailDir = File(context.filesDir, ThumbnailManager.THUMBNAIL_DIR)
                if (thumbnailDir.exists()) {
                    thumbnailDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            val fileId = file.nameWithoutExtension.toLongOrNull()
                            if (fileId == null || fileId !in validFileIds) {
                                cleanedThumbnailsSize += file.length()
                                file.delete()
                                cleanedThumbnails++
                            }
                        }
                    }
                }

                Result.success(OrphanedFilesInfo(
                    cleanedEncryptedFiles = cleanedEncrypted,
                    cleanedEncryptedSize = cleanedEncryptedSize,
                    cleanedThumbnails = cleanedThumbnails,
                    cleanedThumbnailsSize = cleanedThumbnailsSize
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val ENCRYPTED_DIR = "encrypted_files"
        private const val TEMP_DIR = "temp"
    }
}

data class OrphanedFilesInfo(
    val cleanedEncryptedFiles: Int,
    val cleanedEncryptedSize: Long,
    val cleanedThumbnails: Int,
    val cleanedThumbnailsSize: Long
)
