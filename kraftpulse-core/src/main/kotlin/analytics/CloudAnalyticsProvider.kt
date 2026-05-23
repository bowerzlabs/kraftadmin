package analytics

import model.QueryEvent
import telemetry.KraftTelemetryEvent


class CloudAnalyticsProvider(

) : AnalyticsProvider {
    override fun track(event: KraftTelemetryEvent) {
        TODO("Not yet implemented")
    }

    override fun getTrafficTrend(
        interval: TimeInterval,
        range: TimeRange,
        filter: TelemetryFilter
    ): List<TrafficPoint> {
        TODO("Not yet implemented")
    }

    override fun getTopResources(
        limit: Int,
        sortBy: SortMetric
    ): List<ResourceStats> {
        TODO("Not yet implemented")
    }

    override fun getStatusBreakdown(filter: TelemetryFilter): Map<Int, Long> {
        TODO("Not yet implemented")
    }

    override fun getLatencyPercentiles(
        resource: String?,
        range: TimeRange
    ): LatencyReport {
        TODO("Not yet implemented")
    }

    override fun getRegionalDistribution(range: TimeRange): Map<String, Long> {
        TODO("Not yet implemented")
    }

    override fun getSummary(range: TimeRange): AnalyticsSummary {
        TODO("Not yet implemented")
    }

    override fun save(event: QueryEvent) {
        TODO("Not yet implemented")
    }

    override fun getQueriesForTrace(traceId: String): List<QueryEvent> {
        TODO("Not yet implemented")
    }

    override fun getLatestWithDetails(limit: Int): List<TelemetryWithQueries> {
        TODO("Not yet implemented")
    }


}