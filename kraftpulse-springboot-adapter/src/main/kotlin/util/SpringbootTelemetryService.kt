package util

import analytics.AnalyticsReader
import analytics.TelemetryWithQueries
import config.KraftPulseSpringKraftAdminProperties
import config.TelemetryProvider
import json.KraftJsonSerializer
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent
import telemetry.KraftTelemetryService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestTemplate
import com.kraftadmin.model.KraftTelemetryEvent
import telemetry.SQLiteTelemetryProvider
import java.util.concurrent.ConcurrentLinkedQueue

open class SpringBootTelemetryService(
    private val properties: KraftPulseSpringKraftAdminProperties,
    private val serializer: KraftJsonSerializer,
    private val analyticsReader: AnalyticsReader,
    private val commonStore: SQLiteTelemetryProvider,
    private val restTemplate: RestTemplate,
) : KraftTelemetryService {

    private val logger = LoggerFactory.getLogger(SpringBootTelemetryService::class.java)
    private val liveBuffer = ConcurrentLinkedQueue<KraftTelemetryEvent>()
    private val maxBufferSize = 500

    // ✅ Prevents overlapping flushes if a previous flush is still running
    // when the next scheduled tick fires (e.g. cloud is slow/hanging)
    private val flushInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

//    @Async("kraftTelemetryExecutor")
//    override fun record(event: KraftTelemetryEvent) {
//        if (liveBuffer.size >= maxBufferSize) liveBuffer.poll()
//        liveBuffer.add(event)
//        commonStore.save(event)
//    }

//    @Async("kraftTelemetryExecutor")
//    override fun recordException(exceptionData: PulseExceptionEntry) = commonStore.saveException(exceptionData)
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordTaskEvent(taskEvent: KraftTaskEvent) = commonStore.saveTask(taskEvent)
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordHttpClientEvent(event: KraftHttpClientEvent) = commonStore.saveHttpClientEvent(event)
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordQueryEvent(event: QueryEvent) = commonStore.save(event)


    @Async("kraftTelemetryExecutor")
    override fun record(event: KraftTelemetryEvent) {
        if (liveBuffer.size >= maxBufferSize) liveBuffer.poll()
        liveBuffer.add(event)
        commonStore.save(event)
        telemetry.micrometer.KraftPulseTelemetryMeters.recordRequest(event)
    }

    @Async("kraftTelemetryExecutor")
    override fun recordException(exceptionData: PulseExceptionEntry) {
        commonStore.saveException(exceptionData)
        telemetry.micrometer.KraftPulseTelemetryMeters.recordException(exceptionData)
    }

    @Async("kraftTelemetryExecutor")
    override fun recordTaskEvent(taskEvent: KraftTaskEvent) {
        commonStore.saveTask(taskEvent)
        telemetry.micrometer.KraftPulseTelemetryMeters.recordTask(taskEvent)
    }

    @Async("kraftTelemetryExecutor")
    override fun recordHttpClientEvent(event: KraftHttpClientEvent) {
        commonStore.saveHttpClientEvent(event)
        telemetry.micrometer.KraftPulseTelemetryMeters.recordHttpClientCall(event)
    }

    @Async("kraftTelemetryExecutor")
    override fun recordQueryEvent(event: QueryEvent) {
        commonStore.save(event)
        telemetry.micrometer.KraftPulseTelemetryMeters.recordQuery(event)
    }

//    @Async("kraftTelemetryExecutor")
//    override fun record(event: KraftTelemetryEvent) {
//        if (liveBuffer.size >= maxBufferSize) liveBuffer.poll()
//        liveBuffer.add(event)
//        commonStore.save(event)
//        micrometer.recordRequest(event) // ✅ free Prometheus metric, zero extra config for consumer
//    }
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordException(exceptionData: PulseExceptionEntry) {
//        commonStore.saveException(exceptionData)
//        micrometer.recordException(exceptionData)
//    }
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordTaskEvent(taskEvent: KraftTaskEvent) {
//        commonStore.saveTask(taskEvent)
//        micrometer.recordTask(taskEvent)
//    }
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordHttpClientEvent(event: KraftHttpClientEvent) {
//        commonStore.saveHttpClientEvent(event)
//        micrometer.recordHttpClientCall(event)
//    }
//
//    @Async("kraftTelemetryExecutor")
//    override fun recordQueryEvent(event: QueryEvent) {
//        commonStore.save(event)
//        micrometer.recordQuery(event)
//    }

    // Runs on KraftPulse's OWN scheduler bean, never the parent app's default
    // TaskScheduler — see kraftPulseScheduler bean below. This guarantees zero
    // contention with the consuming application's own @Scheduled jobs/thread pool.
    @Scheduled(fixedRate = 10000, scheduler = "kraftPulseScheduler")
    override fun flushToCloud() {
        if (properties.telemetryConfig.provider == TelemetryProvider.LOCAL) return

        // ✅ Skip this tick entirely if the previous flush hasn't finished —
        // avoids two overlapping POSTs to the same ingest endpoint
        if (!flushInProgress.compareAndSet(false, true)) {
            logger.debug("Flush already in progress, skipping this tick.")
            return
        }

        try {
            doFlush()
        } finally {
            flushInProgress.set(false)
        }
    }

    private fun doFlush() {
        val batch = try {
            commonStore.fetchUnsyncedBatch(limit = 300)
        } catch (e: Exception) {
            logger.error("Failed to read unsynced batch from SQLite: {}", e.message, e)
            return
        }

        if (batch.events.isEmpty() && batch.allOrphanedRecordsEmpty()) {
            logger.debug("No pending telemetry to flush.")
            return
        }

        val traceIdsToUpdate = batch.events.mapNotNull { it.traceId }.distinct()
        val targetBaseUrl = properties.telemetryConfig.cloudUrl ?: "http://localhost:8090"
        val cloudUrl = "${targetBaseUrl.removeSuffix("/")}/api/telemetry/ingest"

        logger.info(
            "Flushing batch to {}: {} events, {} queries, {} exceptions, {} tasks, {} http events",
            cloudUrl, batch.events.size, batch.queries.size, batch.exceptions.size,
            batch.tasks.size, batch.httpClientEvents.size
        )

        try {
            val response = restTemplate.postForEntity(cloudUrl, batch, String::class.java)

            if (response.statusCode.is2xxSuccessful) {
                commonStore.markAsSynced(traceIdsToUpdate)
                commonStore.markOrphansAsSynced()
                logger.info(
                    "✅ Flushed: {} events, {} correlated items.",
                    batch.events.size, batch.totalCorrelatedCount()
                )
            } else {
                // ✅ Non-exception non-2xx responses were previously silently dropped —
                // this is likely part of why data wasn't reaching the cloud
                logger.warn(
                    "⚠️ Cloud ingest returned non-2xx status {} for {} events. Body: {}. Will retry next tick.",
                    response.statusCode, batch.events.size, response.body
                )
            }
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            // 4xx — schema/auth problems, won't fix itself on retry
            logger.error(
                "❌ Cloud rejected batch (4xx): {} — {}. Dropping poison-pill batch.",
                e.statusCode, e.responseBodyAsString
            )
            commonStore.markAsSynced(traceIdsToUpdate)
            commonStore.markOrphansAsSynced()
        } catch (e: org.springframework.web.client.HttpServerErrorException) {
            // 5xx — cloud-side issue, worth retrying
            logger.warn("⚠️ Cloud server error (5xx): {}. Traces remain in SQLite for retry.", e.statusCode)
        } catch (e: org.springframework.web.client.ResourceAccessException) {
            // Connection refused, timeout, DNS failure — network-level, very common cause of "never reaches cloud"
            logger.warn("⚠️ Network error reaching cloud endpoint [{}]: {}. Traces remain in SQLite for retry.", cloudUrl, e.message)
        } catch (e: Exception) {
            logger.error("❌ Unexpected error during flush: {}. Traces remain in SQLite for retry.", e.message, e)
        }
    }

    override fun getPulse(limit: Int): List<KraftTelemetryEvent> = liveBuffer.toList().takeLast(limit).reversed()
    override fun purge() = liveBuffer.clear()
    override fun getDashboardOverview(limit: Int): List<TelemetryWithQueries> = analyticsReader.fetchLatestWithQueries(limit)
    override fun getComprehensiveDeepDive(traceId: String): Map<String, Any?> = analyticsReader.fetchComprehensiveDeepDive(traceId)
    override fun <T> getPageData(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T> = fetchAllPaged(table, limit, offset, clazz)
    override fun <T> fetchAllPaged(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T> = analyticsReader.fetchAllPaged(table, limit, offset, clazz)
}

