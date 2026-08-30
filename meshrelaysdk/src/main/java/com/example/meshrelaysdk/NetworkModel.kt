package com.example.meshrelaysdk

import com.google.gson.annotations.SerializedName

enum class EmergencyType(val code: Byte, val stringValue: String) {
    SOS_SIGNAL(0, "SOS SIGNAL"),
    MEDICAL(1, "Medical"),
    WILDFIRE(2, "Wildfire"),
    FLOOD(3, "Flood"),
    EARTHQUAKE(4, "Earthquake"),
    STUCK(5, "Stuck"),
    NEED_FOOD(6, "Need Food"),
    ANTI_HARASSMENT(7, "Anti-Harassment");

    companion object {
        fun fromCode(code: Byte) = entries.find { it.code == code } ?: SOS_SIGNAL
    }
}

enum class SeverityLevel(val code: Byte, val stringValue: String) {
    LOW(0, "Low"),
    MEDIUM(1, "Medium"),
    HIGH(2, "High"),
    CRITICAL(3, "Critical");

    companion object {
        fun fromCode(code: Byte) = entries.find { it.code == code } ?: LOW
    }
}

// Maps directly to FastAPI's SosPacketIn model
data class SosPacketDto(
    @SerializedName("message_id") val messageId: String,
    @SerializedName("original_timestamp") val originalTimestamp: Long, // Sent in seconds
    @SerializedName("lat") val lat: Float,
    @SerializedName("lon") val lon: Float,
    @SerializedName("severity") val severity: String,
    @SerializedName("request_type") val requestType: String,
    @SerializedName("ttl") val ttl: Int,
    @SerializedName("location_source") val locationSource: String = "GPS FIX"
)