package controller

import analytics.LatencyReport
import analytics.ResourceStats
import analytics.SortMetric
import analytics.TelemetryFilter
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import telementary.KraftTelemetryService
import analytics.TelemetryWithQueries
import analytics.TimeInterval
import analytics.TimeRange
import analytics.TrafficPoint
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/admin/api/monitoring")
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
class KraftSpringMonitoringController(
    private val telemetryService: KraftTelemetryService
) {

    @GetMapping("/dashboard")
    fun getDashboardOverview(@RequestParam(defaultValue = "50") limit: Int): List<TelemetryWithQueries> {
        return telemetryService.getDashboardOverview(limit)
    }

    @GetMapping("/traces/{traceId}")
    fun getTraceDeepDive(@PathVariable traceId: String): Map<String, Any?> {
        return telemetryService.getComprehensiveDeepDive(traceId)
    }

    @GetMapping("/exceptions")
    fun getExceptionsPage(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): List<PulseExceptionEntry> {
        return telemetryService.getPageData("kraft_exceptions", limit, offset, PulseExceptionEntry::class.java)
    }

    @GetMapping("/tasks")
    fun getTasksPage(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): List<KraftTaskEvent> {
        return telemetryService.getPageData("kraft_tasks", limit, offset, KraftTaskEvent::class.java)
    }

    @GetMapping("/outbound-http")
    fun getOutboundHttpPage(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): List<KraftHttpClientEvent> {
        return telemetryService.getPageData("kraft_http_client_events", limit, offset, KraftHttpClientEvent::class.java)
    }

    /**
     * Aggregated traffic pulse with interval control (MINUTELY, HOURLY, DAILY)
     */
    @GetMapping("/traffic/trend")
    fun getTrafficTrend(
        @RequestParam(defaultValue = "24") hours: Int,
        @RequestParam(defaultValue = "HOURLY") interval: TimeInterval
    ): List<TrafficPoint> {
        val range = TimeRange(Instant.now().minus(hours.toLong(), ChronoUnit.HOURS), Instant.now())
//        return analyticsProvider.getTrafficTrend(interval, range, TelemetryFilter())
        return emptyList()
    }

    /**
     * Returns P50, P95, and P99 latencies for the system or a specific resource.
     */
    @GetMapping("/latency/report")
    fun getLatencyReport(
        @RequestParam(required = false) resource: String?,
        @RequestParam(defaultValue = "24") hours: Int
    ): LatencyReport? {
        val range = TimeRange(Instant.now().minus(hours.toLong(), ChronoUnit.HOURS), Instant.now())
//        return analyticsProvider.getLatencyPercentiles(resource, range)
        return null
    }

    /**
     * Top resources by traffic, error rate, or latency.
     */
    @GetMapping("/resources/top")
    fun getTopResources(
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "REQUEST_COUNT") sortBy: SortMetric
    ): List<ResourceStats> {
//        return analyticsProvider.getTopResources(limit, sortBy)
        return emptyList()
    }

    /**
     * Distribution of status codes for the pie chart / health indicators.
     */
    @GetMapping("/distribution/status")
    fun getStatusDistribution(@RequestParam(required = false) resource: String?): Map<Int, Long> {
//        return analyticsProvider.getStatusBreakdown(TelemetryFilter(resource = resource))
        return emptyMap()
    }
}