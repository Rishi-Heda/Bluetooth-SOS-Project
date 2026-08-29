package com.example.myapplication.data.model

import java.nio.ByteBuffer

data class SosPacket(
    val messageId: Int,      // 4 bytes: Truncated hash for deduplication
    val latitude: Float,     // 3 bytes: Compressed via fixed-point math
    val longitude: Float,    // 3 bytes: Compressed via fixed-point math
    val severity: Byte,      // 1 byte: Bitmask (e.g., 0x01 = Medical)
    val ttl: Byte            // 1 byte: Hop counter
) {
    /**
     * Serializes the packet into a 12-byte array for BLE advertising.
     */
    fun toByteArray(): ByteArray {
        val bytes = ByteArray(12)

        // 1. Message ID (4 bytes) - Standard bit shifting
        bytes[0] = (messageId shr 24).toByte()
        bytes[1] = (messageId shr 16).toByte()
        bytes[2] = (messageId shr 8).toByte()
        bytes[3] = messageId.toByte()

        // 2. Latitude (3 bytes)
        // Normalize -90 to 90 -> 0 to 180. Multiply by 10,000 for precision (max 1,800,000 fits in 3 bytes)
        val latInt = ((latitude + 90f) * 10000f).toInt()
        bytes[4] = (latInt shr 16).toByte()
        bytes[5] = (latInt shr 8).toByte()
        bytes[6] = latInt.toByte()

        // 3. Longitude (3 bytes)
        // Normalize -180 to 180 -> 0 to 360. Multiply by 10,000 for precision (max 3,600,000 fits in 3 bytes)
        val lonInt = ((longitude + 180f) * 10000f).toInt()
        bytes[7] = (lonInt shr 16).toByte()
        bytes[8] = (lonInt shr 8).toByte()
        bytes[9] = lonInt.toByte()

        // 4. Severity (1 byte)
        bytes[10] = severity

        // 5. TTL (1 byte)
        bytes[11] = ttl

        return bytes
    }

    companion object {
        /**
         * Deserializes a received 12-byte BLE payload back into an SosPacket object.
         */
        fun fromByteArray(bytes: ByteArray): SosPacket? {
            if (bytes.size < 12) return null // Drop corrupted/partial packets

            // 1. Message ID
            // We use 'and 0xFF' because Kotlin Bytes are signed; this prevents negative bit-shift corruption
            val messageId = (bytes[0].toInt() and 0xFF shl 24) or
                    (bytes[1].toInt() and 0xFF shl 16) or
                    (bytes[2].toInt() and 0xFF shl 8) or
                    (bytes[3].toInt() and 0xFF)

            // 2. Latitude
            val latInt = (bytes[4].toInt() and 0xFF shl 16) or
                    (bytes[5].toInt() and 0xFF shl 8) or
                    (bytes[6].toInt() and 0xFF)
            val latitude = (latInt.toFloat() / 10000f) - 90f

            // 3. Longitude
            val lonInt = (bytes[7].toInt() and 0xFF shl 16) or
                    (bytes[8].toInt() and 0xFF shl 8) or
                    (bytes[9].toInt() and 0xFF)
            val longitude = (lonInt.toFloat() / 10000f) - 180f

            // 4. & 5. Severity and TTL
            val severity = bytes[10]
            val ttl = bytes[11]

            return SosPacket(messageId, latitude, longitude, severity, ttl)
        }

        /**
         * Generates a unique 4-byte ID for new messages created on this device.
         */
        fun generateId(): Int {
            // A simple hash of the current time and a random number to prevent collisions
            return (System.currentTimeMillis().hashCode() * 31) + (0..10000).random()
        }
    }
}