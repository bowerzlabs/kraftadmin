package com.kraftadmin.annotations

import com.kraftadmin.enums.ChartType
import com.kraftadmin.enums.MetricAggregation
import com.kraftadmin.enums.MetricPeriod

/**
 * Registers a metric for an entity on the KraftAdmin dashboard.
 *
 * Three modes, checked in this order:
 *
 * GROUPED (groupByField set) — records are grouped by the given field
 * (scalar, e.g. "email", or a relation path, e.g. "post.title") and the
 * aggregation is applied per group, producing a breakdown/ranking chart.
 * Takes precedence over dateField if both are set.
 *
 * TIME-SERIES (dateField set, groupByField blank) — records are grouped
 * into [period] buckets and the aggregation is applied per bucket.
 *
 * SNAPSHOT (both blank) — the aggregation is applied once across all
 * matching records, with no breakdown or time dimension.
 *
 * @property name Unique identifier for the metric.
 * @property label Human-readable label. Falls back to [name] if blank.
 * @property type Aggregation applied: COUNT, SUM, AVG, MIN, MAX.
 * @property dateField Date/timestamp field for time-series bucketing.
 * Ignored if [groupByField] is set. Leave blank for snapshot/grouped modes.
 * @property valueField Numeric field used by SUM/AVG/MIN/MAX. Not required
 * for COUNT.
 * @property groupByField Field to group records by — a plain scalar
 * (e.g. "email") or a relation path (e.g. "post.title"). When set, this
 * takes priority over dateField/period.
 * @property groupByLimit Max number of groups returned, ordered by value
 * descending (e.g. top 10 posts by comment count). Guards against
 * unbounded cardinality fields.
 * @property period Time bucket granularity. Ignored in grouped/snapshot mode.
 * @property chart Chart visualization used to display the metric.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JvmRepeatable(KraftAdminMetrics::class)
annotation class KraftAdminMetric(
    val name: String,
    val label: String = "",
    val type: MetricAggregation,
    val dateField: String = "",
    val valueField: String = "",
    val groupByField: String = "",
    val groupByLimit: Int = 10,
    val period: MetricPeriod = MetricPeriod.MONTH,
    val chart: ChartType = ChartType.LINE
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KraftAdminMetrics(val value: Array<KraftAdminMetric>)