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

@Dao
interface LivePhotoDao {
    @Query("SELECT * FROM live_photos ORDER BY timestamp DESC")
    fun getAllLivePhotos(): Flow<List<LivePhotoRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLivePhoto(record: LivePhotoRecord): Long

    @Query("DELETE FROM live_photos WHERE id = :id")
    suspend fun deleteLivePhotoById(id: Int)
}

@Database(entities = [LivePhotoRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun livePhotoDao(): LivePhotoDao

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
