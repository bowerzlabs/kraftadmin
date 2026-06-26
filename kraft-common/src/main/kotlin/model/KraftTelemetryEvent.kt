package com.kraftadmin.model

import security.AdminUserDTO
import java.util.UUID

// Canonical Telemetry Event
data class KraftTelemetryEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val traceId: String,
    val type: TelemetryType,
    val resource: String,
    val action: String,
    val durationMs: Long,
    val status: Int,
    val actor: AdminUserDTO? = null,

    // BI & SEO Expansion
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val deviceType: String? = null, // Mobile/Desktop (parsed from UA)
    val referer: String? = null,    // Essential for SEO/Traffic source tracking
    val geolocation: GeoData? = null,
    val impact: EventImpact? = null,
    val request: RequestDetails? = null,
)

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

enum class TelemetryType {
    HTTP_REQUEST,       // Inbound HTTP
    OUTBOUND_REQUEST,   // RestTemplate / WebClient / Feign calls
    DATA_ACCESS,        // Database queries
    CACHE,              // Cache hit/miss/eviction
    QUEUE,              // Kafka / RabbitMQ / SQS publish or consume
    JOB,                // @Scheduled, Quartz, Spring Batch steps
    BATCH,              // Full Spring Batch job lifecycle
    FILE_STORAGE,       // Upload / Download
    AUTH,               // Login, token validation, permission check
    SYSTEM,             // Internal KraftAdmin overhead
    AUDIT,               // Business-level audit trail (create/update/delete)
    RESOURCE_USAGE
}

enum class EventStatus {
    SUCCESS,
    CLIENT_ERROR,       // 4xx equivalent
    SERVER_ERROR,       // 5xx equivalent
    TIMEOUT,
    CANCELLED
}

data class EventImpact(
    val rowsAffected: Int = 0,
    val bytesProcessed: Long = 0,
    val isCacheHit: Boolean = false,

    // Aggregated SQL Intelligence
    val queryCount: Int = 0,        // Total queries in this trace
    val totalDbDurationMs: Long = 0, // Sum of all durationMs from child queries
    val hasSlowQueries: Boolean = false,
    val hasNPlusOne: Boolean = false,

    val estimatedCostUsd: Double? = null
)

data class ErrorDetails(
    val exceptionClass: String,
    val message: String?,
    val stackSummary: String           // First 5 lines — enough to identify the source
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

data class GeoData(
    val country: String?,
    val city: String?,
    val lat: Double?,
    val lon: Double?
)

data class RequestDetails(
    val method: String,            // GET, POST, etc.
    val path: String,              // The URI template (e.g., "/api/sponsors/{id}")
    val fullUrl: String?,          // The actual URL with query params
    val protocol: String = "HTTP/1.1",

    // Connectivity Intelligence
    val ipAddress: String?,
    val userAgent: String?,
    val referer: String?,          // Vital for knowing where traffic is coming from
    val origin: String?,           // CORS context

    // Performance & SEO
    val deviceType: String?,       // Mobile, Tablet, Desktop (parsed from UA)
    val browser: String?,          // Chrome, Safari, etc.
    val os: String?,               // Linux, MacOS, Android

    // Routing Context
    val controller: String?,       // The Spring Controller handling this
    val handlerMethod: String?,    // The specific function in the code
    val routePattern: String?,     // Essential for grouping disparate URLs

    // Localization
    val locale: String?,           // e.g., "en-US" or "sw-KE"
    val timezone: String?          // Helpful for correlating system time vs user time
)