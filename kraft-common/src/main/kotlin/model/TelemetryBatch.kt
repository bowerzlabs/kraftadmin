package com.kraftadmin.model

import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent

/**
 * Universal ClickHouse Ingestion Batch Model
 */
data class ClickHouseTelemetryBatch(
    val events: List<KraftTelemetryEvent>,
    val queries: List<QueryEvent>,
    val exceptions: List<PulseExceptionEntry>,
    val tasks: List<KraftTaskEvent>,
    val httpClientEvents: List<KraftHttpClientEvent>
) {
    fun allOrphanedRecordsEmpty(): Boolean =
        queries.isEmpty() && exceptions.isEmpty() && tasks.isEmpty() && httpClientEvents.isEmpty()

    fun totalCorrelatedCount(): Int =
        queries.size + exceptions.size + tasks.size + httpClientEvents.size
}