package util // Kept matching your package setup

import analytics.TelemetryWithQueries
import config.KraftPulseSpringKraftAdminProperties
import json.KraftJsonSerializer
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import telementary.KraftTelemetryService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestTemplate
import telemetry.KraftTelemetryEvent
import telemetry.SQLiteTelemetryProvider
import java.util.concurrent.ConcurrentLinkedQueue

open class SpringBootTelemetryService(
    private val properties: KraftPulseSpringKraftAdminProperties,
    private val serializer: KraftJsonSerializer,
    private val commonStore: SQLiteTelemetryProvider
) : KraftTelemetryService {

    private val logger = LoggerFactory.getLogger(SpringBootTelemetryService::class.java)
    private val restTemplate = RestTemplate()

    // RAM buffer for the instant Svelte Dashboard (Pulse)
    private val liveBuffer = ConcurrentLinkedQueue<KraftTelemetryEvent>()
    private val maxBufferSize = 500

    @Async("kraftTelemetryExecutor")
    override fun record(event: KraftTelemetryEvent) {
        if (liveBuffer.size >= maxBufferSize) liveBuffer.poll()
        liveBuffer.add(event)

        // Durable Persistence (SQLite Core Telemetry)
        commonStore.save(event)
    }

    @Async("kraftTelemetryExecutor")
    override fun recordException(exceptionData: PulseExceptionEntry) {
        logger.debug("Recording exception for trace: {}", exceptionData.traceId)
        commonStore.saveException(exceptionData)
    }

    override fun recordTaskEvent(taskEvent: KraftTaskEvent) {
        logger.debug("Recording task-event for trace: {}", taskEvent.traceId)
        commonStore.saveTask(taskEvent)
    }

    override fun recordHttpClientEvent(event: KraftHttpClientEvent) {
        logger.info("Outbound HTTP request logged: $event")
        commonStore.saveHttpClientEvent(event)
    }

    // FIXED: Synchronized non-destructive outbox sync pattern targeting ClickHouse ingest
    @Scheduled(fixedRate = 120000)
    override fun flushToCloud() {
        // 1. Fetch pending unsynced events from SQLite (Outbox Pattern)
        val pendingEvents = commonStore.fetchBatch(limit = 100)

        // Safe Early Exit: Don't waste cloud processing or network sockets when idle
        if (pendingEvents.isEmpty()) {
            logger.debug("KraftPulse Outbox Mirror: 0 pending events found in SQLite. Skipping emission.")
            return
        }

        val batch = TelemetryBatch(events = pendingEvents)

        // Extract all trace identifiers within this execution window
        val traceIdsToUpdate = pendingEvents.map { it.traceId }.distinct()

        try {
            val cloudUrl = "${properties.telemetryConfig.cloudUrl}/api/telemetry/ingest"
            val response = restTemplate.postForEntity(cloudUrl, batch, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                // 2. State Transition: Flip flags from 0 to 1 safely across all tables via Trace IDs
                commonStore.markAsSynced(traceIdsToUpdate)
                logger.info("Successfully emitted ${pendingEvents.size} relational traces to Kraftpulse Cloud")
            }
        } catch (e: Exception) {
            // Drop Poison Pill records smoothly if a structural 400 error occurs
            if (e.message?.contains("400") == true) {
                logger.error("Drop Poison Pill Batch: Server rejected payload with 400 Bad Request. Marking traces as synced to clear pipeline blocking.")
                commonStore.markAsSynced(traceIdsToUpdate)
            } else {
                logger.warn("Cloud Emission Failed: {}. Traces remain safely intact inside SQLite for automatic retry.", e.message)
            }
        }
    }

    override fun getPulse(limit: Int): List<KraftTelemetryEvent> =
        liveBuffer.toList().takeLast(limit).reversed()

    override fun purge() = liveBuffer.clear()

    override fun getDashboardOverview(limit: Int): List<TelemetryWithQueries> {
        return commonStore.fetchLatestWithQueries(limit)
    }

    override fun getComprehensiveDeepDive(traceId: String): Map<String, Any?> {
        return commonStore.fetchComprehensiveDeepDive(traceId)
    }

    override fun <T> getPageData(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T> {
        return commonStore.fetchAllPaged(table, limit, offset, clazz)
    }

    // FIXED: Cleaned up background worker task to prune data deterministically every 6 hours
    // This removes write overhead during live application requests and handles locks gracefully.
    @Scheduled(fixedRate = 21600000)
    fun runJanitorMaintenance() {
        try {
            logger.info("KraftPulse Maintenance: Starting scheduled outbox pruning cycle...")
            commonStore.pruneOldEvents(retentionDays = 7)
            logger.info("KraftPulse Maintenance: Pruning completed. Old synced traces cleared safely.")
        } catch (e: Exception) {
            logger.error("KraftPulse Maintenance Error: Failed to execute janitor background worker: {}", e.message)
        }
    }
}

// Ensure the Jackson mapper inside Spring Boot targets the precise field collection name
data class TelemetryBatch(
    val events: List<KraftTelemetryEvent>
)