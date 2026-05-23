package util

import analytics.TelemetryWithQueries
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import telemetry.SQLiteTelemetryProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
class KraftSpringLoggingService(
    private val sqliteProvider: SQLiteTelemetryProvider,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

//    fun push(entry: KraftLogEntry) {
//        // Convert LogEntry to TelemetryEvent to store it in SQLite
//        val event = entry.toTelemetryEvent()
//        sqliteProvider.save(event)
//
//        // Still publish for the real-time SSE stream
//        applicationEventPublisher.publishEvent(entry)
//    }

    fun getAll(): List<TelemetryWithQueries> {
        // Fetch the last 500 rows directly from SQLite
//        return sqliteProvider.fetchLatest(500)
        return sqliteProvider.fetchLatestWithQueries(500)
    }
}