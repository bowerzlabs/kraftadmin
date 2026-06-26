package model

import java.util.UUID

/**
 * The monitoring payload for KraftPulse exceptions.
 * Retention: 7 Days (SQLite) / Indefinite (Cloud)
 */
data class PulseExceptionEntry(
    val id: String = UUID.randomUUID().toString(),
    val traceId: String,
    val tenantId: String?,
    val userId: String?,
    val timestamp: Long = System.currentTimeMillis(),

    // Core Error Details
    val exceptionClass: String,
    val message: String,
    val stackTrace: String, // Full stack
    val stackSummary: String, // First 5-10 lines for quick grouping

    // Request Context
    val path: String,
    val method: String,
    val statusCode: Int,
    val requestHeaders: Map<String, String> = emptyMap(),
    val queryParams: Map<String, List<String>> = emptyMap(),

    // Infrastructure & Environment Context
    val hostName: String? = null,
    val environment: String? = null, // e.g., "prod", "staging"
    val version: String? = null,     // App version for regression tracking

    // Impact Assessment
    val isHandled: Boolean = false, // True if caught by global handler
    val metadata: Map<String, Any?> = emptyMap()
)