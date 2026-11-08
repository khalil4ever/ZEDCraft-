package com.example.data.local

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

@Entity(tableName = "youtube_api_cache")
data class YouTubeCacheEntity(
    @PrimaryKey val cacheKey: String,
    val endpoint: String,
    val payloadJson: String,
    val cachedAtMs: Long,
    val ttlMs: Long
)

@Entity(tableName = "local_saved_work")
data class LocalSavedWorkEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val workType: String,
    val title: String,
    val subtitle: String,
    val score: Int,
    val payloadJson: String,
    val channelId: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface YouTubeCacheDao {
    @Query("SELECT * FROM youtube_api_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCacheEntry(key: String): YouTubeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCacheEntry(entry: YouTubeCacheEntity)

    @Query("DELETE FROM youtube_api_cache WHERE cachedAtMs + ttlMs < :nowMs")
    suspend fun purgeExpired(nowMs: Long)

    @Query("DELETE FROM youtube_api_cache")
    suspend fun clearAllCache()

    @Query("SELECT COUNT(*) FROM youtube_api_cache")
    fun observeCacheCount(): Flow<Int>

    @Query("SELECT * FROM local_saved_work ORDER BY createdAtMs DESC")
    fun observeLocalSavedWork(): Flow<List<LocalSavedWorkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalSavedWork(item: LocalSavedWorkEntity): Long

    @Query("DELETE FROM local_saved_work WHERE localId = :id")
    suspend fun deleteLocalSavedWork(id: Long)

    @Query("DELETE FROM local_saved_work")
    suspend fun clearLocalSavedWork()
}

@Database(
    entities = [YouTubeCacheEntity::class, LocalSavedWorkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ZedCraftLocalDatabase : RoomDatabase() {
    abstract fun cacheDao(): YouTubeCacheDao

    companion object {
        @Volatile
        private var INSTANCE: ZedCraftLocalDatabase? = null

        fun getInstance(context: Context): ZedCraftLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZedCraftLocalDatabase::class.java,
                    "zedcraft_local_cache.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
