package cn.logicliu.filesafe.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.logicliu.filesafe.data.entity.TrashItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashItemDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrashItems(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items WHERE id = :id")
    suspend fun getTrashItemById(id: Long): TrashItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trashItem: TrashItemEntity): Long

    @Delete
    suspend fun delete(trashItem: TrashItemEntity)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM trash_items")
    suspend fun clearAll()

    @Query("SELECT * FROM trash_items WHERE originalName LIKE '%' || :query || '%'")
    fun searchTrashItems(query: String): Flow<List<TrashItemEntity>>
}
