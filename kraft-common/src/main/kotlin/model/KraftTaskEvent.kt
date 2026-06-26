package model

import java.util.UUID

enum class KraftTaskType {
    SCHEDULED,
    ASYNC,
    APPLICATION_EVENT,
    COMMAND
}

enum class KraftTaskStatus {
    START,
    SUCCESS,
    FAILURE,
    EMITTED
}

/**
 * Captures CPU and Memory footprint for a specific execution window.
 */
data class ResourceUsage(
    val cpuUsagePercent: Double?,         // Average CPU utilization (%)
    val memoryUsedBytes: Long,            // Peak or final memory usage
    val threadCount: Int? = null          // Number of threads spawned by the task
)

/**
 * Represents a background task execution within the KraftAdmin ecosystem.
 */
data class KraftTaskEvent(
    val id: String = UUID.randomUUID().toString(),
    val traceId: String,

    // --- Task Identification ---
    val name: String,
    val type: KraftTaskType,
    val status: KraftTaskStatus,

    // --- Execution Context ---
    val durationMs: Long = 0,
    val errorMessage: String? = null,

    // --- Resource Tracking ---
    val resourceUsage: ResourceUsage? = null, // Correlation with system health

    // --- Identity & Infrastructure ---
    val nodeIdentifier: String? = null,
    val retryCount: Int = 0,
    val triggerSource: String? = null,
    val taskMetadata: Map<String, String> = emptyMap(),

    val createdAt: Long = System.currentTimeMillis()
)
