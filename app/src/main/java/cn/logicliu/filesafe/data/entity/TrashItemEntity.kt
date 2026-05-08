package cn.logicliu.filesafe.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trash_items")
data class TrashItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalName: String,
    val encryptedPath: String,
    val originalPath: String,
    val size: Long,
    val mimeType: String?,
    val deletedAt: Long,
    val itemType: String,
    val originalFolderId: Long?
)
