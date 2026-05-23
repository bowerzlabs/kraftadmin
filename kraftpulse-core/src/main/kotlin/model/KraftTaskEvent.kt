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

data class KraftTaskEvent(
    val id: String = UUID.randomUUID().toString(),
    val traceId: String,
    val name: String,
    val type: KraftTaskType,
    val status: KraftTaskStatus,
    val durationMs: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)