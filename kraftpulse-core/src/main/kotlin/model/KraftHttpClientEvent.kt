package model

import java.util.UUID

data class KraftHttpClientEvent(
    val id: String = UUID.randomUUID().toString(),
    val traceId: String,
    val url: String,
    val method: String,
    val statusCode: Int,
    val durationMs: Long,
    val createdAt: Long = System.currentTimeMillis()
)