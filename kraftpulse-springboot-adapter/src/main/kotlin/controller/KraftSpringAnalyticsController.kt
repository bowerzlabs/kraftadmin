package controller

import analytics.AnalyticsProvider
import analytics.LatencyReport
import analytics.ResourceStats
import analytics.SortMetric
import analytics.TelemetryFilter
import analytics.TimeInterval
import analytics.TimeRange
import analytics.TrafficPoint
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/admin/api/analytics")
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
class KraftSpringAnalyticsController(
    private val analyticsProvider: AnalyticsProvider
) {


    /**
     * Aggregated traffic pulse with interval control (MINUTELY, HOURLY, DAILY)
     */
    @GetMapping("/traffic/trend")
    fun getTrafficTrend(
        @RequestParam(defaultValue = "24") hours: Int,
        @RequestParam(defaultValue = "HOURLY") interval: TimeInterval
    ): List<TrafficPoint> {
        val range = TimeRange(Instant.now().minus(hours.toLong(), ChronoUnit.HOURS), Instant.now())
        return analyticsProvider.getTrafficTrend(interval, range, TelemetryFilter())
    }

    /**
     * Returns P50, P95, and P99 latencies for the system or a specific resource.
     */
    @GetMapping("/latency/report")
    fun getLatencyReport(
        @RequestParam(required = false) resource: String?,
        @RequestParam(defaultValue = "24") hours: Int
    ): LatencyReport {
        val range = TimeRange(Instant.now().minus(hours.toLong(), ChronoUnit.HOURS), Instant.now())
        return analyticsProvider.getLatencyPercentiles(resource, range)
    }

    /**
     * Top resources by traffic, error rate, or latency.
     */
    @GetMapping("/resources/top")
    fun getTopResources(
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(defaultValue = "REQUEST_COUNT") sortBy: SortMetric
    ): List<ResourceStats> {
        return analyticsProvider.getTopResources(limit, sortBy)
    }

    /**
     * Distribution of status codes for the pie chart / health indicators.
     */
    @GetMapping("/distribution/status")
    fun getStatusDistribution(@RequestParam(required = false) resource: String?): Map<Int, Long> {
        return analyticsProvider.getStatusBreakdown(TelemetryFilter(resource = resource))
    }

}

