package model

import java.util.UUID

/**
 * The monitoring payload for KraftPulse exceptions.
 * Retention: 7 Days (SQLite) / Indefinite (Cloud)
 */
data class PulseExceptionEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val traceId: String,
    val tenantId: String?,
    val userId: String?,
    val exceptionClass: String,
    val message: String,
    val stackTrace: String,
    val path: String,
    val method: String,
    val statusCode: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // Captures underlying request details (Query Params, Headers)
    val metadata: Map<String, Any?> = emptyMap()
)
