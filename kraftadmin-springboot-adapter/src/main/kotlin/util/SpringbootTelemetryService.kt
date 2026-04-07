package com.kraftadmin.util

import com.kraftadmin.config.SpringKraftAdminProperties
import com.kraftadmin.utils.telementary.KraftTelemetryEvent
import com.kraftadmin.utils.telementary.KraftTelemetryService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

@Service
@ConditionalOnMissingBean(KraftTelemetryService::class)
class SpringBootTelemetryService(
    private val properties: SpringKraftAdminProperties
) : KraftTelemetryService {

    private val logger = LoggerFactory.getLogger(SpringBootTelemetryService::class.java)
    private val restTemplate = org.springframework.web.client.RestTemplate()

    private val liveBuffer = ConcurrentLinkedQueue<KraftTelemetryEvent>()
    private val cloudBuffer = ConcurrentLinkedQueue<KraftTelemetryEvent>()
    private val maxBufferSize = 500

    @Async("kraftTelemetryExecutor")
    override fun record(event: KraftTelemetryEvent) {
        //  Local Pulse (Svelte UI)
        if (liveBuffer.size >= maxBufferSize) liveBuffer.poll()
        liveBuffer.add(event)

        // Queue for Cloud BI
        cloudBuffer.add(event)

        logger.info("TELEMETRY: {} | {}ms | User: {}", event.action, event.durationMs, event.actor)
    }

    // 3. Batch Flush to Cloud every 10 seconds
    @Scheduled(fixedRate = 10000)
    override fun flushToCloud() {
        if (cloudBuffer.isEmpty()) return

        val currentBatchList = mutableListOf<KraftTelemetryEvent>()
        val batch = TelemetryBatch(events = currentBatchList)
        while (cloudBuffer.isNotEmpty() && batch.events.size < 100) {
            cloudBuffer.poll()?.let { currentBatchList.add(it) }
        }

        try {
            val cloudUrl = "${properties.telemetryConfig.cloudUrl}/api/telemetry/ingest"
            restTemplate.postForEntity(cloudUrl, batch, String::class.java)
            logger.debug("Successfully emitted ${batch.events.size} events to Kraftpulse")
        } catch (e: Exception) {
            logger.warn("Cloud Telemetry Emission Failed: ${e.message}")
            // Optional: Re-queue failed events if critical
        }
    }

    override fun getPulse(limit: Int): List<KraftTelemetryEvent> = liveBuffer.toList().takeLast(limit).reversed()

    override fun purge() = liveBuffer.clear()
}

data class TelemetryBatch(
    val events: List<KraftTelemetryEvent>
)

