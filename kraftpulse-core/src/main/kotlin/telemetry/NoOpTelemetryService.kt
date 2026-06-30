package telemetry


import analytics.TelemetryWithQueries
import com.kraftadmin.model.KraftTelemetryEvent
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent

/**
 * A silent placeholder for when telemetry is disabled.
 * Prevents "Bean Not Found" errors in the Spring Context.
 */
class NoOpTelemetryService : KraftTelemetryService {
    override fun record(event: KraftTelemetryEvent) { /* Silently ignore */ }
    override fun getPulse(limit: Int): List<KraftTelemetryEvent> {
        return emptyList()
    }

    override fun purge() {
    }

    override fun flushToCloud() {
    }

    override fun recordException(exceptionData: PulseExceptionEntry) { /* Silently ignore */ }
    override fun recordTaskEvent(taskEvent: KraftTaskEvent) {
    }

    override fun recordHttpClientEvent(event: KraftHttpClientEvent) {
    }

    override fun recordQueryEvent(event: QueryEvent) {
        TODO("Not yet implemented")
    }

    override fun getDashboardOverview(limit: Int): List<TelemetryWithQueries> {
        TODO("Not yet implemented")
    }

    override fun getComprehensiveDeepDive(traceId: String): Map<String, Any?> {
        TODO("Not yet implemented")
    }

    override fun <T> getPageData(
        table: String,
        limit: Int,
        offset: Int,
        clazz: Class<T>
    ): List<T> {
        TODO("Not yet implemented")
    }

    override fun <T> fetchAllPaged(
        table: String,
        limit: Int,
        offset: Int,
        clazz: Class<T>
    ): List<T> {
        TODO("Not yet implemented")
    }

    fun saveException(entry: PulseExceptionEntry) { /* Silently ignore */ }
}