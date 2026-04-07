package com.kraftadmin.utils.telementary

import java.time.Instant
import java.util.UUID

/**
 * The standard envelope for all telemetry emitted by KraftAdmin.
 */
data class KraftTelemetryEvent1(
    val id: UUID = UUID.randomUUID(),
    val timestamp: Instant = Instant.now(),
    val actor: AdminActor,          // Who did it?
    val action: String,             // What happened? (e.g., "RESOURCE_CREATED")
    val resource: String,           // Which entity? (e.g., "Talent")
    val metadata: Map<String, Any?>, // Contextual data (IP, Browser, Region)
    val changes: List<FieldChange>? = null, // What specifically changed?
    val durationMs: Long? = null    // How long did the operation take? (Performance BI)
)

data class AdminActor(
    val id: String,
    val username: String,
    val roles: List<String>,
    val ipAddress: String
)

data class FieldChange(
    val field: String,
    val oldValue: Any?,
    val newValue: Any?
)

data class KraftTelemetryEvent2(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: TelemetryType,
    val resource: String,      // e.g., "Sponsor", "User"
    val action: String,        // e.g., "FETCH_ALL", "SAVE", "DELETE"
    val durationMs: Long,      // Latency in milliseconds
    val status: Int,           // 200, 500, etc.
    val actor: String?         // The user who triggered the event
)

enum class TelemetryType {
    DATA_ACCESS,    // Database operations
    FILE_STORAGE,   // Uploads/Downloads
    AUTH,           // Login/Security checks
    SYSTEM          // Internal library overhead
}

data class KraftTelemetryEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: TelemetryType,
    val resource: String,
    val action: String,
    val durationMs: Long,
    val status: Int,
    val actor: String?,

    // BI & SEO Expansion
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val deviceType: String? = null, // Mobile/Desktop (parsed from UA)
    val referer: String? = null,    // Essential for SEO/Traffic source tracking
    val geolocation: GeoData? = null
)

data class GeoData(
    val country: String?,
    val city: String?,
    val lat: Double?,
    val lon: Double?
)