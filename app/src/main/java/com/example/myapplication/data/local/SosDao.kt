package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SosDao {

    // THE DEDUPLICATION ENGINE:
    // Returns the row ID if inserted, or -1 if it was a duplicate and ignored.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPacket(packet: SosEntity): Long

    // For the UI: Observe all messages chronologically to display on the HomeScreen
    @Query("SELECT * FROM sos_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<SosEntity>>

    // For the BLE Advertiser: Fetch up to 3 messages that still have TTL remaining
    @Query("SELECT * FROM sos_messages WHERE ttl > 0 AND syncStatus = 0 LIMIT 3")
    suspend fun getPacketsForRelay(): List<SosEntity>

    // For the Gateway Uploader: Fetch ALL pending messages when internet is restored
    @Query("SELECT * FROM sos_messages WHERE syncStatus = 0")
    suspend fun getPendingUploads(): List<SosEntity>

    // Mark messages as uploaded so we stop broadcasting them locally
    @Query("UPDATE sos_messages SET syncStatus = 1 WHERE messageId IN (:messageIds)")
    suspend fun markAsUploaded(messageIds: List<Int>)

    // Decrement TTL before rebroadcasting (so messages eventually die)
    @Query("UPDATE sos_messages SET ttl = ttl - 1 WHERE messageId = :id")
    suspend fun decrementTtl(id: Int)
}