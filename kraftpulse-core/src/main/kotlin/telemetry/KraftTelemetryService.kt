package telemetry

import analytics.TelemetryWithQueries
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent

/**
 * Implemented per-framework (Spring Boot, Ktor, Spark, Javalin).
 * Each implementation is responsible for:
 *   1. Persisting events to the durable store (SQLite/etc — unchanged).
 *   2. ALSO emitting the same data to a MeterRegistry, if one is available,
 *      so consumers using Prometheus/Grafana get dashboards for free
 *      without any extra KraftPulse-specific UI work on their part.
 *
 * Item 2 is OPTIONAL per-framework — MeterRegistry may not exist in
 * Spark/Javalin contexts unless the consumer wires Micrometer themselves.
 * Implementations must null-check / no-op gracefully if absent.
 */
interface KraftTelemetryService {
    fun record(event: KraftTelemetryEvent)
    fun getPulse(limit: Int = 100): List<KraftTelemetryEvent>
    fun purge()
    fun flushToCloud()
    fun recordException(exceptionData: PulseExceptionEntry)
    fun recordTaskEvent(taskEvent: KraftTaskEvent)
    fun recordHttpClientEvent(event: KraftHttpClientEvent)
    fun recordQueryEvent(event: QueryEvent)
    fun getDashboardOverview(limit: Int): List<TelemetryWithQueries>
    fun getComprehensiveDeepDive(traceId: String): Map<String, Any?>
    fun <T> getPageData(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T>
    fun <T> fetchAllPaged(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T>
}