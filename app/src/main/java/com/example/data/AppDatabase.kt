package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "live_photos")
data class LivePhotoRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val coverPath: String,
    val videoPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isEmbedded: Boolean = false
)

@Entity(tableName = "webdav_config")
data class WebDavConfig(
    @PrimaryKey val id: Int = 1,
    val serverUrl: String,
    val username: String,
    val password: String,
    val isConnected: Boolean = false,
    val lastSyncTime: Long = 0L
)

@Dao
interface LivePhotoDao {
    @Query("SELECT * FROM live_photos ORDER BY timestamp DESC")
    fun getAllLivePhotos(): Flow<List<LivePhotoRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLivePhoto(record: LivePhotoRecord): Long

    @Query("DELETE FROM live_photos WHERE id = :id")
    suspend fun deleteLivePhotoById(id: Int)
}

@Dao
interface WebDavDao {
    @Query("SELECT * FROM webdav_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): WebDavConfig?

    @Query("SELECT * FROM webdav_config WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<WebDavConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: WebDavConfig)
}

@Database(entities = [LivePhotoRecord::class, WebDavConfig::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun livePhotoDao(): LivePhotoDao
    abstract fun webDavDao(): WebDavDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "live_photos_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
