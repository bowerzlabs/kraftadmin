package persistence.metrics

import com.kraftadmin.annotations.KraftAdminMetric
import com.kraftadmin.spi.MetricBucket
import com.kraftadmin.spi.MetricGroup
import com.kraftadmin.spi.MetricMode
import com.kraftadmin.spi.MetricResult

fun buildSnapshotResult(metric: KraftAdminMetric, value: Double): MetricResult {
    return MetricResult(
        name = metric.name,
        label = metric.label.ifBlank { metric.name },
        chartType = metric.chart.name,
        mode = MetricMode.SNAPSHOT,
        currentPeriodValue = value
    )
}

fun buildTimeSeriesResult(metric: KraftAdminMetric, buckets: List<MetricBucket>): MetricResult {
    val current = buckets.lastOrNull()?.value ?: 0.0
    val previous = if (buckets.size >= 2) buckets[buckets.size - 2].value else 0.0
    val growth = if (previous == 0.0) null else ((current - previous) / previous) * 100.0

    return MetricResult(
        name = metric.name,
        label = metric.label.ifBlank { metric.name },
        chartType = metric.chart.name,
        mode = MetricMode.TIME_SERIES,
        buckets = buckets,
        currentPeriodValue = current,
        previousPeriodValue = previous,
        growthPercent = growth
    )
}


fun buildGroupedResult(metric: KraftAdminMetric, groups: List<MetricGroup>): MetricResult {
    return MetricResult(
        name = metric.name,
        label = metric.label.ifBlank { metric.name },
        chartType = metric.chart.name,
        mode = MetricMode.GROUPED,
        groups = groups.sortedByDescending { it.value }.take(metric.groupByLimit)
    )
}