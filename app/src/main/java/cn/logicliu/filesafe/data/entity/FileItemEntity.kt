package cn.logicliu.filesafe.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "files")
data class FileItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val encryptedPath: String,
    val size: Long,
    val mimeType: String?,
    val folderId: Long?,
    val createdAt: Long,
    val modifiedAt: Long,
    val isEncrypted: Boolean = true
)
