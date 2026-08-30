package com.example.meshrelaysdk.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SosDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPacket(packet: SosEntity): Long

    @Query("SELECT * FROM sos_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<SosEntity>>

    @Query("SELECT * FROM sos_messages WHERE ttl > 0 AND syncStatus = 0 AND isDismissed = 0 ORDER BY timestamp ASC")
    suspend fun getPacketsForRelay(): List<SosEntity>

    @Query("SELECT * FROM sos_messages WHERE syncStatus = 0")
    suspend fun getPendingUploads(): List<SosEntity>

    // Observes the database for new messages so the gateway can upload them instantly
    @Query("SELECT * FROM sos_messages WHERE syncStatus = 0")
    fun observePendingUploads(): Flow<List<SosEntity>>

    @Query("UPDATE sos_messages SET syncStatus = 1 WHERE messageId IN (:messageIds)")
    suspend fun markAsUploaded(messageIds: List<Int>)

    @Query("UPDATE sos_messages SET isDismissed = 1 WHERE messageId = :messageId")
    suspend fun dismissPacket(messageId: Int)
}