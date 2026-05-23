package com.kraftadmin

import com.kraftadmin.repository.VenueRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@Service
class KraftPulseTestBackgroundService(
    private val eventPublisher: ApplicationEventPublisher,
    private val repository: VenueRepository,
    private val restTemplate: RestTemplate
) {
    private val logger = LoggerFactory.getLogger(KraftPulseTestBackgroundService::class.java)
    private val counter = AtomicInteger(0)

    @Scheduled(fixedRate = 10000)
    fun runScheduledTask() {
        val currentCount = counter.incrementAndGet()
        logger.info("⏰ Background Cron Job Executing... Pass #$currentCount")

        // TEST REAL JPA DATABASE CALL VIA REPOSITORY INTERCEPT
        try {
            logger.info("Triggering database read via VenueRepository...")
            val venues = repository.findAll()
            logger.info("Database Read Success. Found ${venues.size} venues recorded.")
        } catch (e: Exception) {
            logger.error("Telemetry Database Intercept Failed: ${e.message}", e)
        }

        // TEST OUTBOUND HTTP CALL VIA INTERCEPTED RESTTEMPLATE
        try {
            val targetUrl = "https://jsonplaceholder.typicode.com/todos/1"
            logger.info("🌐 Triggering outbox telemetry RestTemplate fetch target: $targetUrl")
            val response = restTemplate.getForObject(targetUrl, String::class.java)
            logger.debug("Outbound Response payload length: ${response?.length ?: 0}")
        } catch (e: Exception) {
            logger.warn("Outbound HTTP call failed or timed out: ${e.message}")
        }

        // FORCE REAL DOMAIN EXCEPTION VIA MISSING VENUE RECORD (Every 3rd run)
        if (currentCount % 3 == 0) {
            val missingId = "hjh761"
            logger.info("🔍 Intentionally looking up a non-existent venue to force error capture: $missingId")

            // This runs a valid SELECT query, returns Optional.EMPTY, then throws a clean target exception
            repository.findById(missingId).orElseThrow {
                NoSuchElementException("Venue record not found for ID: $missingId in BaseEntity auditing module.")
            }
        }
    }

    fun publishTestEvent(msg: String) {
        logger.info("Broadcasting custom application context event: $msg")
        eventPublisher.publishEvent(KraftPulseSampleEvent(this, msg))
    }

}