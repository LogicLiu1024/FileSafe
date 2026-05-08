package cn.logicliu.filesafe.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cn.logicliu.filesafe.data.entity.FileItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileItemDao {
    @Query("SELECT * FROM files WHERE folderId IS NULL ORDER BY name ASC")
    fun getRootFiles(): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE folderId = :folderId ORDER BY name ASC")
    fun getFilesByFolder(folderId: Long): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Long): FileItemEntity?

    @Query("SELECT * FROM files WHERE path = :path")
    suspend fun getFileByPath(path: String): FileItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: FileItemEntity): Long

    @Update
    suspend fun update(file: FileItemEntity)

    @Delete
    suspend fun delete(file: FileItemEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM files WHERE name LIKE '%' || :query || '%'")
    fun searchFiles(query: String): Flow<List<FileItemEntity>>
}
