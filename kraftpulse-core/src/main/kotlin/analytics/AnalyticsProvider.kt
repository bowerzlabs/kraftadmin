package analytics

import model.QueryEvent
import telemetry.KraftTelemetryEvent
import java.time.Instant

interface AnalyticsProvider {
    /**
     * Records the event.
     * Implementation: SQLite (Immediate/WAL), ClickHouse (Buffered Batch).
     */
    fun track(event: KraftTelemetryEvent)

    // --- BI & TRAFFIC ---

    /** Aggregated traffic over time with optional filtering */
    fun getTrafficTrend(
        interval: TimeInterval = TimeInterval.HOURLY,
        range: TimeRange,
        filter: TelemetryFilter = TelemetryFilter()
    ): List<TrafficPoint>

    /** Top performing or most failing resources */
    fun getTopResources(limit: Int = 10, sortBy: SortMetric = SortMetric.REQUEST_COUNT): List<ResourceStats>


    // --- RELIABILITY & PERFORMANCE ---

    /** Distribution of HTTP status codes (2xx, 4xx, 5xx) */
    fun getStatusBreakdown(filter: TelemetryFilter): Map<Int, Long>

    /** * Calculates latency percentiles (P50, P95, P99).
     * Crucial for detecting "Slow" vs "Broken" systems.
     */
    fun getLatencyPercentiles(resource: String?, range: TimeRange): LatencyReport

    // --- GEOGRAPHIC & CLIENT ---

    /** Grouping by IP/Country for the Regional Pulse map */
    fun getRegionalDistribution(range: TimeRange): Map<String, Long>

    fun getSummary(range: TimeRange): AnalyticsSummary

    fun save(event: QueryEvent)

    /**
     * Fetches the underlying SQL execution details for a specific trace.
     * Used for deep-dive diagnostics in the UI.
     */
    fun getQueriesForTrace(traceId: String): List<QueryEvent>

    /**
     * Fetches recent telemetry events, each including its associated SQL queries.
     * Perfect for the "Live Feed" diagnostic view in the UI.
     */
    fun getLatestWithDetails(limit: Int): List<TelemetryWithQueries>
}

// --- SUPPORTING MODELS ---

data class TelemetryFilter(
    val resource: String? = null,
    val actor: String? = null,
    val statusGroup: Int? = null // e.g., 5 for 5xx errors
)

data class TelemetryWithQueries(
    val event: KraftTelemetryEvent,
    val queries: List<QueryEvent>
)

data class LatencyReport(val p50: Double, val p95: Double, val p99: Double, val avg: Double)

data class ResourceStats(
    val resource: String,
    val requestCount: Long,
    val errorRate: Double,
    val avgLatency: Double
)

enum class TimeInterval { MINUTELY, HOURLY, DAILY }
enum class SortMetric { REQUEST_COUNT, ERROR_RATE, LATENCY }
data class TimeRange(val start: Instant, val end: Instant)
data class TrafficPoint(val timestamp: Long, val count: Int)
