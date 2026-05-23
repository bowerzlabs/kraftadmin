//package com.kraftadmin.utils.analytics
//
//import com.kraftadmin.utils.telementary.KraftTelemetryEvent
//import com.kraftadmin.utils.telementry.SQLiteTelemetryProvider
//import java.sql.ResultSet
//
////class LocalAnalyticsProvider(
////    private val sqLiteTelemetryProvider: SQLiteTelemetryProvider
////) : AnalyticsProvider {
////
////    override fun track(event: KraftTelemetryEvent) {
////        sqLiteTelemetryProvider.save(event)
////    }
////
////    override fun getGlobalTraffic(hours: Int): List<TrafficPoint> {
////        val cutoff = System.currentTimeMillis() - (hours * 3600000L)
////        // Group by hour (3600000ms)
////        val sql = """
////            SELECT (created_at / 3600000) * 3600000 as hour, COUNT(*) as count
////            FROM telemetry_outbox
////            WHERE created_at >= ?
////            GROUP BY hour
////            ORDER BY hour ASC
////        """.trimIndent()
////
////        return queryPoints(sql, cutoff)
////    }
////
////    override fun getTrafficByResource(resource: String, hours: Int): List<TrafficPoint> {
////        val cutoff = System.currentTimeMillis() - (hours * 3600000L)
////        // Extracting resource from the JSON payload column
////        val sql = """
////            SELECT (created_at / 3600000) * 3600000 as hour, COUNT(*) as count
////            FROM telemetry_outbox
////            WHERE created_at >= ?
////            AND json_extract(payload, '$.resource') = ?
////            GROUP BY hour
////            ORDER BY hour ASC
////        """.trimIndent()
////
////        return queryPoints(sql, cutoff, resource)
////    }
////
////    override fun getStatusDistribution(resource: String?): Map<Int, Int> {
////        val sql = if (resource != null) {
////            "SELECT json_extract(payload, '$.status') as status, COUNT(*) as count FROM telemetry_outbox WHERE json_extract(payload, '$.resource') = ? GROUP BY status"
////        } else {
////            "SELECT json_extract(payload, '$.status') as status, COUNT(*) as count FROM telemetry_outbox GROUP BY status"
////        }
////
////        val distribution = mutableMapOf<Int, Int>()
////        executeInternal(sql, resource) { rs ->
////            while (rs.next()) {
////                val status = rs.getInt("status")
////                if (status > 0) distribution[status] = rs.getInt("count")
////            }
////        }
////        return distribution
////    }
////
////    override fun getAverageLatency(resource: String): Double {
////        val sql = "SELECT AVG(json_extract(payload, '$.durationMs')) as avg_lat FROM telemetry_outbox WHERE json_extract(payload, '$.resource') = ?"
////        var average = 0.0
////        executeInternal(sql, resource) { rs ->
////            if (rs.next()) average = rs.getDouble("avg_lat")
////        }
////        return average
////    }
////
////    // Helper to keep SQL boilerplate out of the main logic
////    private fun queryPoints(sql: String, vararg params: Any): List<TrafficPoint> {
////        val points = mutableListOf<TrafficPoint>()
////        executeInternal(sql, *params) { rs ->
////            while (rs.next()) {
////                points.add(TrafficPoint(rs.getLong("hour"), rs.getInt("count")))
////            }
////        }
////        return points
////    }
////
////    private fun executeInternal(sql: String, vararg params: Any?, block: (ResultSet) -> Unit) {
////        try {
////            sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
////                params.forEachIndexed { i, p ->
////                    when (p) {
////                        is Long -> pstmt.setLong(i + 1, p)
////                        is Int -> pstmt.setInt(i + 1, p)
////                        is String -> pstmt.setString(i + 1, p)
////                    }
////                }
////                block(pstmt.executeQuery())
////            }
////        } catch (e: Exception) {
////            System.err.println("Analytics Query Failed: ${e.message}")
////        }
////    }
////
////}
//
//
//import java.time.LocalDateTime
//import java.time.ZoneId
//import java.time.format.DateTimeFormatter
//
//class LocalAnalyticsProvider(
//    private val sqLiteTelemetryProvider: SQLiteTelemetryProvider
//) : AnalyticsProvider {
//
//    override fun track(event: KraftTelemetryEvent) {
//        // Direct write to the structured SQLite table
//        sqLiteTelemetryProvider.save(event)
//    }
//
//    override fun getTrafficTrend(
//        interval: TimeInterval,
//        range: TimeRange,
//        filter: TelemetryFilter
//    ): List<TrafficPoint> {
//        val sqliteFormat = when (interval) {
//            TimeInterval.MINUTELY -> "%Y-%m-%d %H:%M:00"
//            TimeInterval.HOURLY -> "%Y-%m-%d %H:00:00"
//            TimeInterval.DAILY -> "%Y-%m-%d 00:00:00"
//        }
//
//        val sql = """
//            SELECT strftime('$sqliteFormat', datetime(created_at / 1000, 'unixepoch')) as bucket,
//                   COUNT(*) as count
//            FROM kraft_telemetry
//            WHERE created_at BETWEEN ? AND ?
//            ${if (filter.resource != null) "AND resource = ?" else ""}
//            GROUP BY bucket ORDER BY bucket ASC
//        """.trimIndent()
//
//        val results = mutableListOf<TrafficPoint>()
//        sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
//            pstmt.setLong(1, range.start.toEpochMilli())
//            pstmt.setLong(2, range.end.toEpochMilli())
//            if (filter.resource != null) pstmt.setString(3, filter.resource)
//
//            val rs = pstmt.executeQuery()
//            while (rs.next()) {
//                results.add(TrafficPoint(
//                    timestamp = parseSqliteDate(rs.getString("bucket")),
//                    count = rs.getInt("count")
//                ))
//            }
//        }
//        return results
//    }
//
//    override fun getTopResources(limit: Int, sortBy: SortMetric): List<ResourceStats> {
//        val orderBy = when (sortBy) {
//            SortMetric.REQUEST_COUNT -> "cnt DESC"
//            SortMetric.ERROR_RATE -> "err_rate DESC"
//            SortMetric.LATENCY -> "avg_lat DESC"
//        }
//
//        val sql = """
//            SELECT resource,
//                   COUNT(*) as cnt,
//                   AVG(duration_ms) as avg_lat,
//                   (SUM(CASE WHEN status >= 400 THEN 1 ELSE 0 END) * 1.0 / COUNT(*)) as err_rate
//            FROM kraft_telemetry
//            GROUP BY resource
//            ORDER BY $orderBy
//            LIMIT ?
//        """.trimIndent()
//
//        val results = mutableListOf<ResourceStats>()
//        sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
//            pstmt.setInt(1, limit)
//            val rs = pstmt.executeQuery()
//            while (rs.next()) {
//                results.add(ResourceStats(
//                    resource = rs.getString("resource"),
//                    requestCount = rs.getLong("cnt"),
//                    errorRate = rs.getDouble("err_rate"),
//                    avgLatency = rs.getDouble("avg_lat")
//                ))
//            }
//        }
//        return results
//    }
//
//    override fun getStatusBreakdown(filter: TelemetryFilter): Map<Int, Long> {
//        val sql = """
//            SELECT status, COUNT(*) as cnt
//            FROM kraft_telemetry
//            WHERE 1=1 ${if (filter.resource != null) "AND resource = ?" else ""}
//            GROUP BY status
//        """.trimIndent()
//
//        val breakdown = mutableMapOf<Int, Long>()
//        sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
//            if (filter.resource != null) pstmt.setString(1, filter.resource)
//            val rs = pstmt.executeQuery()
//            while (rs.next()) {
//                breakdown[rs.getInt("status")] = rs.getLong("cnt")
//            }
//        }
//        return breakdown
//    }
//
//    override fun getLatencyPercentiles(resource: String?, range: TimeRange): LatencyReport {
//        // SQLite P95/P99 trick using subqueries to find the Nth value in an ordered set
//        val sql = """
//            SELECT
//                AVG(duration_ms) as avg_lat,
//                (SELECT duration_ms FROM kraft_telemetry WHERE created_at BETWEEN ? AND ? ${if (resource != null) "AND resource = ?" else ""} ORDER BY duration_ms LIMIT 1 OFFSET CAST((SELECT COUNT(*) FROM kraft_telemetry WHERE created_at BETWEEN ? AND ?) * 0.5 AS INT)) as p50,
//                (SELECT duration_ms FROM kraft_telemetry WHERE created_at BETWEEN ? AND ? ${if (resource != null) "AND resource = ?" else ""} ORDER BY duration_ms LIMIT 1 OFFSET CAST((SELECT COUNT(*) FROM kraft_telemetry WHERE created_at BETWEEN ? AND ?) * 0.95 AS INT)) as p95,
//                (SELECT duration_ms FROM kraft_telemetry WHERE created_at BETWEEN ? AND ? ${if (resource != null) "AND resource = ?" else ""} ORDER BY duration_ms LIMIT 1 OFFSET CAST((SELECT COUNT(*) FROM kraft_telemetry WHERE created_at BETWEEN ? AND ?) * 0.99 AS INT)) as p99
//            FROM kraft_telemetry
//            WHERE created_at BETWEEN ? AND ?
//            ${if (resource != null) "AND resource = ?" else ""}
//        """.trimIndent()
//
//        sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
//            // This requires 10-13 parameters depending on if resource is null.
//            // For brevity, using a simplified param-setter logic:
//            var i = 1
//            // P50 params
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//            if (resource != null) pstmt.setString(i++, resource)
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//
//            // P95 params
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//            if (resource != null) pstmt.setString(i++, resource)
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//
//            // P99 params
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//            if (resource != null) pstmt.setString(i++, resource)
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//
//            // Main Query params
//            pstmt.setLong(i++, range.start.toEpochMilli()); pstmt.setLong(i++, range.end.toEpochMilli())
//            if (resource != null) pstmt.setString(i++, resource)
//
//            val rs = pstmt.executeQuery()
//            return if (rs.next()) {
//                LatencyReport(
//                    p50 = rs.getDouble("p50"),
//                    p95 = rs.getDouble("p95"),
//                    p99 = rs.getDouble("p99"),
//                    avg = rs.getDouble("avg_lat")
//                )
//            } else LatencyReport(0.0, 0.0, 0.0, 0.0)
//        }
//    }
//
//    override fun getRegionalDistribution(range: TimeRange): Map<String, Long> {
//        // Grouping by IP for the local engine; in Cloud, this swaps to Country/City
//        val sql = """
//            SELECT ip_address, COUNT(*) as cnt
//            FROM kraft_telemetry
//            WHERE created_at BETWEEN ? AND ?
//            GROUP BY ip_address
//            ORDER BY cnt DESC LIMIT 50
//        """.trimIndent()
//
//        val regionalMap = mutableMapOf<String, Long>()
//        sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
//            pstmt.setLong(1, range.start.toEpochMilli())
//            pstmt.setLong(2, range.end.toEpochMilli())
//            val rs = pstmt.executeQuery()
//            while (rs.next()) {
//                regionalMap[rs.getString("ip_address") ?: "unknown"] = rs.getLong("cnt")
//            }
//        }
//        return regionalMap
//    }
//
//    private fun parseSqliteDate(dateStr: String): Long {
//        val format = when {
//            dateStr.length <= 10 -> DateTimeFormatter.ofPattern("yyyy-MM-dd")
//            dateStr.endsWith(":00") -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//            else -> DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
//        }
//
//        return try {
//            LocalDateTime.parse(dateStr, format)
//                .atZone(ZoneId.systemDefault())
//                .toInstant()
//                .toEpochMilli()
//        } catch (e: Exception) {
//            // Fallback for partial date strings from strftime
//            val fullDate = if (dateStr.length == 10) "$dateStr 00:00:00" else dateStr
//            LocalDateTime.parse(fullDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//                .atZone(ZoneId.systemDefault())
//                .toInstant()
//                .toEpochMilli()
//        }
//    }
//}
package analytics

import model.QueryEvent
import telemetry.SQLiteTelemetryProvider
import telemetry.KraftTelemetryEvent
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.use

class LocalAnalyticsProvider(
    private val sqLiteTelemetryProvider: SQLiteTelemetryProvider,
    private val timeZone: ZoneId = ZoneId.systemDefault()
) : AnalyticsProvider {

    private val sqliteOffset: String = run {
        val offset = timeZone.rules.getOffset(Instant.now())
        val totalSeconds = offset.totalSeconds
        val sign = if (totalSeconds >= 0) "+" else "-"
        val abs = Math.abs(totalSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        "%s%02d:%02d".format(sign, hours, minutes)
    }

    override fun track(event: KraftTelemetryEvent) {
        sqLiteTelemetryProvider.save(event)
    }

    override fun getTrafficTrend(
        interval: TimeInterval,
        range: TimeRange,
        filter: TelemetryFilter
    ): List<TrafficPoint> {
        val sqliteFormat = when (interval) {
            TimeInterval.MINUTELY -> "%Y-%m-%d %H:%M:00"
            TimeInterval.HOURLY   -> "%Y-%m-%d %H:00:00"
            TimeInterval.DAILY    -> "%Y-%m-%d 00:00:00"
        }

        val sql = """
            SELECT 
                strftime('$sqliteFormat', datetime(created_at / 1000, 'unixepoch', '$sqliteOffset')) as bucket,
                COUNT(*) as count
            FROM kraft_telemetry
            WHERE created_at BETWEEN ? AND ?
            ${if (filter.resource != null) "AND resource = ?" else ""}
            ${if (filter.actor != null) "AND actor = ?" else ""}
            GROUP BY bucket
            ORDER BY bucket ASC
        """.trimIndent()

        return query(
            sql,
            params = { pstmt ->
                var i = 1
                pstmt.setLong(i++, range.start.toEpochMilli())
                pstmt.setLong(i++, range.end.toEpochMilli())
                if (filter.resource != null) pstmt.setString(i++, filter.resource)
                if (filter.actor != null) pstmt.setString(i++, filter.actor)
//                if (filter.status != null) pstmt.setInt(i, filter.status)
            }
        ) { rs ->
            val points = mutableListOf<TrafficPoint>()
            while (rs.next()) {
                points.add(
                    TrafficPoint(
                        timestamp = parseSqliteDate(rs.getString("bucket")),
                        count = rs.getInt("count")
                    )
                )
            }
            points
        }
    }

    override fun getTopResources(limit: Int, sortBy: SortMetric): List<ResourceStats> {
        val orderBy = when (sortBy) {
            SortMetric.REQUEST_COUNT -> "cnt DESC"
            SortMetric.ERROR_RATE    -> "err_rate DESC"
            SortMetric.LATENCY       -> "avg_lat DESC"
        }

        val sql = """
            SELECT 
                resource,
                COUNT(*) as cnt,
                AVG(duration_ms) as avg_lat,
                SUM(CASE WHEN status >= 400 THEN 1 ELSE 0 END) * 1.0 / COUNT(*) as err_rate
            FROM kraft_telemetry
            GROUP BY resource
            ORDER BY $orderBy
            LIMIT ?
        """.trimIndent()

        return query(
            sql,
            params = { it.setInt(1, limit) }
        ) { rs ->
            val results = mutableListOf<ResourceStats>()
            while (rs.next()) {
                results.add(
                    ResourceStats(
                        resource = rs.getString("resource"),
                        requestCount = rs.getLong("cnt"),
                        errorRate = rs.getDouble("err_rate"),
                        avgLatency = rs.getDouble("avg_lat")
                    )
                )
            }
            results
        }
    }

    override fun getStatusBreakdown(filter: TelemetryFilter): Map<Int, Long> {
        val sql = """
            SELECT status, COUNT(*) as cnt
            FROM kraft_telemetry
            WHERE 1=1
            ${if (filter.resource != null) "AND resource = ?" else ""}
            ${if (filter.actor != null) "AND actor = ?" else ""}
            GROUP BY status
        """.trimIndent()

        return query(
            sql,
            params = { pstmt ->
                var i = 1
                if (filter.resource != null) pstmt.setString(i++, filter.resource)
                if (filter.actor != null) pstmt.setString(i, filter.actor)
            }
        ) { rs ->
            val breakdown = mutableMapOf<Int, Long>()
            while (rs.next()) {
                val status = rs.getInt("status")
                if (status > 0) breakdown[status] = rs.getLong("cnt")
            }
            breakdown
        }
    }

    override fun getLatencyPercentiles(resource: String?, range: TimeRange): LatencyReport {
        val baseWhere = """
            FROM kraft_telemetry
            WHERE created_at BETWEEN ? AND ?
            ${if (resource != null) "AND resource = ?" else ""}
        """.trimIndent()

        fun bindRangeAndResource(pstmt: PreparedStatement, startIndex: Int): Int {
            var i = startIndex
            pstmt.setLong(i++, range.start.toEpochMilli())
            pstmt.setLong(i++, range.end.toEpochMilli())
            if (resource != null) pstmt.setString(i++, resource)
            return i
        }

        fun percentileQuery(percentile: Double): Double {
            val sql = """
                SELECT duration_ms
                $baseWhere
                ORDER BY duration_ms
                LIMIT 1
                OFFSET MAX(0, CAST(
                    (SELECT COUNT(*) $baseWhere) * $percentile AS INT
                ) - 1)
            """.trimIndent()

            return query(
                sql,
                params = { pstmt ->
                    // outer WHERE params
                    var i = bindRangeAndResource(pstmt, 1)
                    // subquery WHERE params
                    bindRangeAndResource(pstmt, i)
                }
            ) { rs ->
                if (rs.next()) rs.getDouble("duration_ms") else 0.0
            }
        }

        val avgSql = "SELECT AVG(duration_ms) as avg_lat $baseWhere"
        val avg = query(
            avgSql,
            params = { pstmt -> bindRangeAndResource(pstmt, 1) }
        ) { rs ->
            if (rs.next()) rs.getDouble("avg_lat") else 0.0
        }

        return LatencyReport(
            p50 = percentileQuery(0.50),
            p95 = percentileQuery(0.95),
            p99 = percentileQuery(0.99),
            avg = avg
        )
    }

    override fun getRegionalDistribution(range: TimeRange): Map<String, Long> {
        val sql = """
            SELECT ip_address, COUNT(*) as cnt
            FROM kraft_telemetry
            WHERE created_at BETWEEN ? AND ?
            GROUP BY ip_address
            ORDER BY cnt DESC
            LIMIT 50
        """.trimIndent()

        return query(
            sql,
            params = { pstmt ->
                pstmt.setLong(1, range.start.toEpochMilli())
                pstmt.setLong(2, range.end.toEpochMilli())
            }
        ) { rs ->
            val map = LinkedHashMap<String, Long>()
            while (rs.next()) {
                map[rs.getString("ip_address") ?: "unknown"] = rs.getLong("cnt")
            }
            map
        }
    }

    override fun getSummary(range: TimeRange): AnalyticsSummary {
        val sql = """
            SELECT
                COUNT(*) as total_requests,
                AVG(duration_ms) as avg_latency,
                SUM(CASE WHEN status >= 400 THEN 1 ELSE 0 END) * 1.0 / COUNT(*) as error_rate,
                COUNT(DISTINCT actor) as unique_actors,
                COUNT(DISTINCT ip_address) as unique_ips
            FROM kraft_telemetry
            WHERE created_at BETWEEN ? AND ?
        """.trimIndent()

        return query(
            sql,
            params = { pstmt ->
                pstmt.setLong(1, range.start.toEpochMilli())
                pstmt.setLong(2, range.end.toEpochMilli())
            }
        ) { rs ->
            if (rs.next()) {
                AnalyticsSummary(
                    totalRequests = rs.getLong("total_requests"),
                    avgLatency = rs.getDouble("avg_latency"),
                    errorRate = rs.getDouble("error_rate"),
                    uniqueActors = rs.getLong("unique_actors"),
                    uniqueIps = rs.getLong("unique_ips")
                )
            } else AnalyticsSummary()
        }
    }

//    override fun save(event: QueryEvent) {
//        TODO("Not yet implemented")
//    }

    override fun save(event: QueryEvent) {
        val sql = """
        INSERT INTO kraft_query_events 
        (trace_id, sql, query_type, entity_name, table_name, duration_ms, 
         rows_returned, rows_affected, is_slow, is_n_plus_one, created_at, payload) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

        execute(sql) { pstmt ->
            pstmt.setString(1, event.traceId)
            pstmt.setString(2, event.sql)
            pstmt.setString(3, event.queryType.name)
            pstmt.setString(4, event.entityName)
            pstmt.setString(5, event.tableName)
            pstmt.setLong(6, event.durationMs)
            pstmt.setInt(7, event.rowsReturned)
            pstmt.setInt(8, event.rowsAffected)
            pstmt.setBoolean(9, event.isSlowQuery)
            pstmt.setBoolean(10, event.isPotentialNPlusOne)
            pstmt.setLong(11, event.startedAt)
            // Use the existing serializer from the provider
            pstmt.setString(12, sqLiteTelemetryProvider.serializer.toJson(event))
        }
    }

    override fun getQueriesForTrace(traceId: String): List<QueryEvent> {
        return sqLiteTelemetryProvider.fetchQueriesForTrace(traceId)
    }

    override fun getLatestWithDetails(limit: Int): List<TelemetryWithQueries> {
        return sqLiteTelemetryProvider.fetchLatestWithQueries(limit)
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private fun parseSqliteDate(dateStr: String): Long {
        val fullDate = if (dateStr.length == 10) "$dateStr 00:00:00" else dateStr
        return try {
            LocalDateTime
                .parse(fullDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .atZone(timeZone)
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            System.err.println("KraftAdmin Analytics: Failed to parse date '$dateStr': ${e.message}")
            0L
        }
    }

    private fun <T> query(
        sql: String,
        params: (PreparedStatement) -> Unit = {},
        mapper: (ResultSet) -> T
    ): T {
        return try {
            sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
                params(pstmt)
                mapper(pstmt.executeQuery())
            }
        } catch (e: Exception) {
            System.err.println("KraftAdmin Analytics Query Failed: ${e.message}")
            throw e
        }
    }

    private fun execute(
        sql: String,
        params: (PreparedStatement) -> Unit
    ) {
        try {
            sqLiteTelemetryProvider.connection.prepareStatement(sql).use { pstmt ->
                params(pstmt)
                pstmt.executeUpdate()
            }
        } catch (e: Exception) {
            System.err.println("KraftAdmin Analytics Execution Failed: ${e.message}")
            throw e
        }
    }
}

