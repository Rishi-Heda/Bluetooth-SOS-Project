package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.myapplication.data.model.SosPacket

@Entity(tableName = "sos_messages")
data class SosEntity(
    @PrimaryKey
    val messageId: Int, 

    val latitude: Float,
    val longitude: Float,
    val emergencyType: Byte, // NEW: 4-bit type packed alongside severity
    val severity: Byte,
    val ttl: Byte,

    val timestamp: Long = System.currentTimeMillis(),

    // 0 = PENDING (needs to be uploaded to cloud)
    // 1 = UPLOADED (successfully hit the Gateway, no longer needs relaying)
    val syncStatus: Int = 0,
    
    // NEW: True if the user dismissed it locally, prevents rebroadcasting without deleting
    val isDismissed: Boolean = false 
) {
    /**
     * Helper to convert the DB row back into a BLE packet for rebroadcasting.
     */
    fun toSosPacket(): SosPacket {
        return SosPacket(messageId, latitude, longitude, emergencyType, severity, ttl)
    }
}