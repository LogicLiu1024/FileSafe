package cn.logicliu.filesafe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.entity.TrashItemEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fileDataStore: DataStore<Preferences> by preferencesDataStore(name = "file_data")

class FileDataStore(private val context: Context) {
    private val dataStore = context.fileDataStore
    private val gson = Gson()

    private val filesKey = stringPreferencesKey("files")
    private val foldersKey = stringPreferencesKey("folders")
    private val trashItemsKey = stringPreferencesKey("trash_items")
    private val nextFileIdKey = longPreferencesKey("next_file_id")
    private val nextFolderIdKey = longPreferencesKey("next_folder_id")
    private val nextTrashIdKey = longPreferencesKey("next_trash_id")

    val files: Flow<List<FileItemEntity>> = dataStore.data.map { preferences ->
        val json = preferences[filesKey] ?: "[]"
        parseJson<List<FileItemEntity>>(json) ?: emptyList()
    }

    val folders: Flow<List<FolderEntity>> = dataStore.data.map { preferences ->
        val json = preferences[foldersKey] ?: "[]"
        parseJson<List<FolderEntity>>(json) ?: emptyList()
    }

    val trashItems: Flow<List<TrashItemEntity>> = dataStore.data.map { preferences ->
        val json = preferences[trashItemsKey] ?: "[]"
        parseJson<List<TrashItemEntity>>(json) ?: emptyList()
    }

    fun getRootFiles(): Flow<List<FileItemEntity>> = files.map { list ->
        list.filter { it.folderId == null }
    }

    fun getRootFolders(): Flow<List<FolderEntity>> = folders.map { list ->
        list.filter { it.parentId == null }
    }

    fun getFilesByFolder(folderId: Long): Flow<List<FileItemEntity>> = files.map { list ->
        list.filter { it.folderId == folderId }
    }

    fun getFoldersByParent(parentId: Long): Flow<List<FolderEntity>> = folders.map { list ->
        list.filter { it.parentId == parentId }
    }

    fun getFileById(fileId: Long): Flow<FileItemEntity?> = files.map { list ->
        list.find { it.id == fileId }
    }

    fun getFolderById(folderId: Long): Flow<FolderEntity?> = folders.map { list ->
        list.find { it.id == folderId }
    }

    fun getTrashItemById(trashItemId: Long): Flow<TrashItemEntity?> = trashItems.map { list ->
        list.find { it.id == trashItemId }
    }

    fun searchFiles(query: String): Flow<List<FileItemEntity>> = files.map { list ->
        list.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun searchFolders(query: String): Flow<List<FolderEntity>> = folders.map { list ->
        list.filter { it.name.contains(query, ignoreCase = true) }
    }

    fun searchTrashItems(query: String): Flow<List<TrashItemEntity>> = trashItems.map { list ->
        list.filter { it.originalName.contains(query, ignoreCase = true) }
    }

    suspend fun insertFile(file: FileItemEntity): Long {
        var newId = 0L
        dataStore.edit { preferences ->
            val currentId = preferences[nextFileIdKey] ?: 1L
            newId = currentId
            preferences[nextFileIdKey] = currentId + 1

            val json = preferences[filesKey] ?: "[]"
            val fileList: MutableList<FileItemEntity> = parseJson(json) ?: mutableListOf()
            fileList.add(file.copy(id = currentId))
            preferences[filesKey] = gson.toJson(fileList)
        }
        return newId
    }

    suspend fun insertFolder(folder: FolderEntity): Long {
        var newId = 0L
        dataStore.edit { preferences ->
            val currentId = preferences[nextFolderIdKey] ?: 1L
            newId = currentId
            preferences[nextFolderIdKey] = currentId + 1

            val json = preferences[foldersKey] ?: "[]"
            val folderList: MutableList<FolderEntity> = parseJson(json) ?: mutableListOf()
            folderList.add(folder.copy(id = currentId))
            preferences[foldersKey] = gson.toJson(folderList)
        }
        return newId
    }

    suspend fun insertTrashItem(trashItem: TrashItemEntity): Long {
        var newId = 0L
        dataStore.edit { preferences ->
            val currentId = preferences[nextTrashIdKey] ?: 1L
            newId = currentId
            preferences[nextTrashIdKey] = currentId + 1

            val json = preferences[trashItemsKey] ?: "[]"
            val trashList: MutableList<TrashItemEntity> = parseJson(json) ?: mutableListOf()
            trashList.add(trashItem.copy(id = currentId))
            preferences[trashItemsKey] = gson.toJson(trashList)
        }
        return newId
    }

    suspend fun updateFile(file: FileItemEntity) {
        dataStore.edit { preferences ->
            val json = preferences[filesKey] ?: "[]"
            val fileList: MutableList<FileItemEntity> = parseJson(json) ?: mutableListOf()
            val index = fileList.indexOfFirst { it.id == file.id }
            if (index != -1) {
                fileList[index] = file
                preferences[filesKey] = gson.toJson(fileList)
            }
        }
    }

    suspend fun updateFolder(folder: FolderEntity) {
        dataStore.edit { preferences ->
            val json = preferences[foldersKey] ?: "[]"
            val folderList: MutableList<FolderEntity> = parseJson(json) ?: mutableListOf()
            val index = folderList.indexOfFirst { it.id == folder.id }
            if (index != -1) {
                folderList[index] = folder
                preferences[foldersKey] = gson.toJson(folderList)
            }
        }
    }

    suspend fun deleteFileById(fileId: Long) {
        dataStore.edit { preferences ->
            val json = preferences[filesKey] ?: "[]"
            val fileList: MutableList<FileItemEntity> = parseJson(json) ?: mutableListOf()
            fileList.removeAll { it.id == fileId }
            preferences[filesKey] = gson.toJson(fileList)
        }
    }

    suspend fun deleteFolderById(folderId: Long) {
        dataStore.edit { preferences ->
            val json = preferences[foldersKey] ?: "[]"
            val folderList: MutableList<FolderEntity> = parseJson(json) ?: mutableListOf()
            folderList.removeAll { it.id == folderId }
            preferences[foldersKey] = gson.toJson(folderList)
        }
    }

    suspend fun deleteTrashItemById(trashItemId: Long) {
        dataStore.edit { preferences ->
            val json = preferences[trashItemsKey] ?: "[]"
            val trashList: MutableList<TrashItemEntity> = parseJson(json) ?: mutableListOf()
            trashList.removeAll { it.id == trashItemId }
            preferences[trashItemsKey] = gson.toJson(trashList)
        }
    }

    suspend fun clearTrash() {
        dataStore.edit { preferences ->
            preferences[trashItemsKey] = "[]"
            preferences[nextTrashIdKey] = 1L
        }
    }

    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences[filesKey] = "[]"
            preferences[foldersKey] = "[]"
            preferences[trashItemsKey] = "[]"
            preferences[nextFileIdKey] = 1L
            preferences[nextFolderIdKey] = 1L
            preferences[nextTrashIdKey] = 1L
        }
    }

    private inline fun <reified T> parseJson(json: String): T? {
        return try {
            val type = object : TypeToken<T>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var instance: FileDataStore? = null

        fun getInstance(context: Context): FileDataStore {
            return instance ?: synchronized(this) {
                instance ?: FileDataStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
