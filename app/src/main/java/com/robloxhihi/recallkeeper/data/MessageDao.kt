package com.robloxhihi.recallkeeper.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY capturedAt DESC LIMIT 3000")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY capturedAt DESC")
    suspend fun all(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE convKey = :convKey AND status = 0 ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun latestNormalInConv(convKey: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE status = 0 AND capturedAt > :after ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun latestNormalGlobal(after: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE notifKey = :notifKey ORDER BY capturedAt DESC LIMIT 1")
    suspend fun byNotifKey(notifKey: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE contentHash = :hash LIMIT 1")
    suspend fun byHash(hash: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE convKey = :convKey AND capturedAt > :after")
    suspend fun countNewerInConv(convKey: String, after: Long): Int

    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Query("UPDATE messages SET status = :status, evidence = :evidence, statusAt = :at WHERE id = :id")
    suspend fun setStatus(id: Long, status: Int, evidence: String, at: Long)

    @Query("UPDATE messages SET notifKey = :notifKey WHERE id = :id")
    suspend fun rebindNotifKey(id: Long, notifKey: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE capturedAt < :before")
    suspend fun prune(before: Long): Int
}
