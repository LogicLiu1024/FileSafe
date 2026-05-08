package cn.logicliu.filesafe.data

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.logicliu.filesafe.data.dao.FileItemDao
import cn.logicliu.filesafe.data.dao.FolderDao
import cn.logicliu.filesafe.data.dao.TrashItemDao
import cn.logicliu.filesafe.data.entity.FileItemEntity
import cn.logicliu.filesafe.data.entity.FolderEntity
import cn.logicliu.filesafe.data.entity.TrashItemEntity

@Database(
    entities = [
        FileItemEntity::class,
        FolderEntity::class,
        TrashItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FileSafeDatabase : RoomDatabase() {
    abstract fun fileItemDao(): FileItemDao
    abstract fun folderDao(): FolderDao
    abstract fun trashItemDao(): TrashItemDao

    companion object {
        const val DATABASE_NAME = "filesafe_database"

        @Volatile
        private var INSTANCE: FileSafeDatabase? = null

        fun getInstance(context: android.content.Context): FileSafeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    FileSafeDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
