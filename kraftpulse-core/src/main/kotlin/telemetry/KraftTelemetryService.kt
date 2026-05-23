package telementary

import analytics.TelemetryWithQueries
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import telemetry.KraftTelemetryEvent

interface KraftTelemetryService {
    /**
     * Records a telemetry event. Implementation should be non-blocking.
     */
    fun record(event: KraftTelemetryEvent)

    /**
     * Fetches the latest 'pulse' data for the UI.
     * This allows the Svelte dashboard to show real-time charts.
     */
    fun getPulse(limit: Int = 100): List<KraftTelemetryEvent>

    /**
     * Optional: Clear telemetry data.
     */
    fun purge()

    fun flushToCloud()

    fun recordException(exceptionData: PulseExceptionEntry)

    fun recordTaskEvent(taskEvent: KraftTaskEvent)
    fun recordHttpClientEvent(event: model.KraftHttpClientEvent)

    fun getDashboardOverview(limit: Int): List<TelemetryWithQueries>

    fun getComprehensiveDeepDive(traceId: String): Map<String, Any?>

    fun <T> getPageData(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T>

}