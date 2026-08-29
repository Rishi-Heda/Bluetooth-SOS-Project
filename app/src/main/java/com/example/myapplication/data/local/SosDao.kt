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

    // For the BLE Advertiser: Fetch ALL messages that still have TTL remaining and
    // haven't been uploaded yet. No LIMIT here — the mesh loop round-robins through
    // whatever comes back, so capping this at a small number would silently starve
    // messages beyond the cap, especially when there's no internet to clear the queue.
    @Query("SELECT * FROM sos_messages WHERE ttl > 0 AND syncStatus = 0 ORDER BY timestamp ASC")
    suspend fun getPacketsForRelay(): List<SosEntity>

    // For the Gateway Uploader: Fetch ALL pending messages when internet is restored
    @Query("SELECT * FROM sos_messages WHERE syncStatus = 0")
    suspend fun getPendingUploads(): List<SosEntity>

    // Mark messages as uploaded so we stop broadcasting them locally
    @Query("UPDATE sos_messages SET syncStatus = 1 WHERE messageId IN (:messageIds)")
    suspend fun markAsUploaded(messageIds: List<Int>)
}