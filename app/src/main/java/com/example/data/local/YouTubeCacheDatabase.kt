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

@Entity(tableName = "saved_ideas_vault")
data class SavedIdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val category: String,
    val title: String,
    val content: String,
    val tags: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "zd_chat_messages")
data class ZdChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String,
    val content: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "creator_checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey val itemKey: String,
    val phase: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false
)

@Entity(tableName = "video_revision_backups")
data class VideoRevisionBackupEntity(
    @PrimaryKey val videoId: String,
    val previousTitle: String,
    val previousDescription: String,
    val previousTagsJson: String,
    val categoryId: String,
    val defaultLanguage: String = "",
    val defaultAudioLanguage: String = "",
    val changedField: String = "",
    val backedUpAtMs: Long = System.currentTimeMillis(),
    val savedAtMs: Long = backedUpAtMs
)

@Dao
interface YouTubeCacheDao {
    @Query("SELECT * FROM youtube_api_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCacheEntry(key: String): YouTubeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCacheEntry(entry: YouTubeCacheEntity)

    @Query("DELETE FROM youtube_api_cache WHERE cacheKey = :key")
    suspend fun invalidateCacheKey(key: String)

    @Query("DELETE FROM youtube_api_cache WHERE cacheKey LIKE :prefix || '%'")
    suspend fun invalidateCacheByPrefix(prefix: String)

    @Query("DELETE FROM youtube_api_cache WHERE cachedAtMs + ttlMs < :nowMs")
    suspend fun purgeExpired(nowMs: Long)

    @Query("DELETE FROM youtube_api_cache")
    suspend fun clearAllCache()

    @Query("DELETE FROM youtube_api_cache")
    suspend fun clearAllApiCache()

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

    // Saved Ideas Vault
    @Query("SELECT * FROM saved_ideas_vault ORDER BY createdAtMs DESC")
    fun observeSavedIdeas(): Flow<List<SavedIdeaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedIdea(idea: SavedIdeaEntity): Long

    @Query("DELETE FROM saved_ideas_vault WHERE id = :id")
    suspend fun deleteSavedIdea(id: Long)

    // ZD Chat History
    @Query("SELECT * FROM zd_chat_messages ORDER BY createdAtMs ASC, id ASC")
    fun observeZdChatMessages(): Flow<List<ZdChatMessageEntity>>

    @Query("SELECT COUNT(*) FROM zd_chat_messages")
    suspend fun getZdChatMessageCount(): Int

    @Query("SELECT * FROM zd_chat_messages ORDER BY createdAtMs DESC, id DESC LIMIT :limit")
    suspend fun getRecentZdChatMessages(limit: Int = 12): List<ZdChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZdChatMessage(message: ZdChatMessageEntity): Long

    @Query("DELETE FROM zd_chat_messages")
    suspend fun clearZdChatMessages()

    // Launch & YPP Checklists
    @Query("SELECT * FROM creator_checklist_items")
    fun observeChecklistItems(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT COUNT(*) FROM creator_checklist_items")
    suspend fun getChecklistCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItem(item: ChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChecklistItemsIgnore(items: List<ChecklistItemEntity>)

    suspend fun seedChecklistIfEmpty(defaults: List<ChecklistItemEntity>) {
        if (getChecklistCount() == 0) {
            insertChecklistItemsIgnore(defaults)
        }
    }

    // Video Revision Backups (for Safe YouTube Writes & Undo / Restore)
    @Query("SELECT * FROM video_revision_backups WHERE videoId = :videoId LIMIT 1")
    suspend fun getVideoRevisionBackup(videoId: String): VideoRevisionBackupEntity?

    @Query("SELECT * FROM video_revision_backups ORDER BY backedUpAtMs DESC")
    fun observeVideoRevisionBackups(): Flow<List<VideoRevisionBackupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideoRevisionBackup(backup: VideoRevisionBackupEntity)

    @Query("DELETE FROM video_revision_backups WHERE videoId = :videoId")
    suspend fun deleteVideoRevisionBackup(videoId: String)
}

@Database(
    entities = [
        YouTubeCacheEntity::class,
        LocalSavedWorkEntity::class,
        SavedIdeaEntity::class,
        ZdChatMessageEntity::class,
        ChecklistItemEntity::class,
        VideoRevisionBackupEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class YouTubeCacheDatabase : RoomDatabase() {
    abstract fun dao(): YouTubeCacheDao
    fun cacheDao(): YouTubeCacheDao = dao()

    companion object {
        @Volatile
        private var INSTANCE: YouTubeCacheDatabase? = null

        fun getInstance(context: Context): YouTubeCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    YouTubeCacheDatabase::class.java,
                    "zedcraft_local_cache.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}

typealias ZedCraftLocalDatabase = YouTubeCacheDatabase
