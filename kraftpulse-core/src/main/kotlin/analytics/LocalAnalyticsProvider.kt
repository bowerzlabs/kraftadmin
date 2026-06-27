package analytics

import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent
import telemetry.SQLiteTelemetryProvider
import com.kraftadmin.model.KraftTelemetryEvent
import java.time.ZoneId

class LocalAnalyticsProvider(
    private val sqLiteTelemetryProvider: SQLiteTelemetryProvider,
    private val timeZone: ZoneId = ZoneId.systemDefault(),
) : AnalyticsReader, TelemetryWriter {

    override fun track(event: KraftTelemetryEvent) = sqLiteTelemetryProvider.save(event)
    override fun save(event: QueryEvent) = sqLiteTelemetryProvider.save(event)
    override fun saveTelemetryEvent(event: KraftTelemetryEvent) = sqLiteTelemetryProvider.save(event)
    override fun saveException(exceptionData: PulseExceptionEntry) = sqLiteTelemetryProvider.saveException(exceptionData)
    override fun saveTask(taskEvent: KraftTaskEvent) = sqLiteTelemetryProvider.saveTask(taskEvent)
    override fun saveHttpClientEvent(event: KraftHttpClientEvent) = sqLiteTelemetryProvider.saveHttpClientEvent(event)

    override fun getTrafficTrend(interval: TimeInterval, range: TimeRange, filter: TelemetryFilter): List<TrafficPoint> =
        sqLiteTelemetryProvider.fetchTrafficTrend(interval, range, filter, timeZone)
    override fun getTopResources(limit: Int, sortBy: SortMetric): List<ResourceStats> =
        sqLiteTelemetryProvider.fetchTopResources(limit, sortBy)
    override fun getStatusBreakdown(filter: TelemetryFilter): Map<Int, Long> =
        sqLiteTelemetryProvider.fetchStatusBreakdown(filter)
    override fun getLatencyPercentiles(resource: String?, range: TimeRange): LatencyReport =
        sqLiteTelemetryProvider.fetchLatencyPercentiles(resource, range)
    override fun getRegionalDistribution(range: TimeRange): Map<String, Long> =
        sqLiteTelemetryProvider.fetchRegionalDistribution(range)
    override fun getSummary(range: TimeRange): AnalyticsSummary =
        sqLiteTelemetryProvider.fetchSummary(range)
    override fun getQueriesForTrace(traceId: String): List<QueryEvent> =
        sqLiteTelemetryProvider.fetchQueriesForTrace(traceId)
    override fun getLatestWithDetails(limit: Int): List<TelemetryWithQueries> =
        sqLiteTelemetryProvider.fetchLatestWithQueries(limit)
    override fun fetchLatestWithQueries(limit: Int): List<TelemetryWithQueries> =
        sqLiteTelemetryProvider.fetchLatestWithQueries(limit)
    override fun fetchComprehensiveDeepDive(traceId: String): Map<String, Any?> =
        sqLiteTelemetryProvider.fetchComprehensiveDeepDive(traceId)
    override fun <T> fetchAllPaged(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T> =
        sqLiteTelemetryProvider.fetchAllPaged(table, limit, offset, clazz)

    /**
     * Triggers the maintenance routine to remove old, synced data
     */
    fun performMaintenance(retentionDays: Int = 7) {
        sqLiteTelemetryProvider.pruneOldEvents(retentionDays)
        sqLiteTelemetryProvider.vacuum()
    }
}