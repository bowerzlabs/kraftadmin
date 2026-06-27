package telemetry.events

import analytics.LocalAnalyticsProvider
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class AnalyticsMaintenanceTask(
    private val analyticsProvider: LocalAnalyticsProvider
) {
    private val logger = LoggerFactory.getLogger(AnalyticsMaintenanceTask::class.java)

    // Runs at 03:00 AM every day
    @Scheduled(cron = "0 0 3 * * ?")
    fun runMaintenance() {
        logger.info("🧹 Starting scheduled telemetry maintenance...")
        try {
            analyticsProvider.performMaintenance(retentionDays = 7)
            logger.info("✅ Telemetry maintenance completed successfully.")
        } catch (e: Exception) {
            logger.error("❌ Maintenance task failed", e)
        }
    }
}