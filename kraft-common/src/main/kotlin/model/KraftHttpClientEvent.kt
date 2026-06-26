package model

import java.util.UUID

/**
 * Represents an outbound HTTP request event captured by KraftAdmin's monitoring pipeline.
 * Used for tracking external service dependencies and outbound API performance.
 */
data class KraftHttpClientEvent(
    /** Unique identifier for this specific event instance. */
    val id: String = UUID.randomUUID().toString(),

    /** The correlation ID linking this event to a specific business trace or transaction. */
    val traceId: String,

    // --- Request Context ---

    /** The target host (e.g., "api.stripe.com"). */
    val host: String,

    /** The full request URI including path and query parameters. */
    val url: String,

    /** The HTTP method used (e.g., "GET", "POST", "PUT"). */
    val method: String,

    // --- Response Metrics ---

    /** The HTTP status code returned by the remote service. */
    val statusCode: Int,

    /** The total time taken for the round-trip, measured in milliseconds. */
    val durationMs: Long,

    /** The size of the response body in bytes, useful for monitoring payload overhead. */
    val responseBodySize: Long = 0,

    // --- Troubleshooting Intelligence ---

    /** Connection timeout setting in milliseconds, if specifically configured for this call. */
    val connectionTimeoutMs: Long? = null,

    /** Descriptive error message if the call failed (e.g., "Connection Refused" or "Timeout"). */
    val errorMessage: String? = null,

    /** The timestamp when this event was recorded. */
    val createdAt: Long = System.currentTimeMillis()
)