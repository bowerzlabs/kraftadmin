package telemetry

import analytics.*
import com.kraftadmin.model.ClickHouseTelemetryBatch
import com.kraftadmin.model.KraftTelemetryEvent
import json.KraftJsonSerializer
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent
import org.flywaydb.core.Flyway
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.use

class SQLiteTelemetryProvider(
    private val appName: String = "default-app",
    val serializer: KraftJsonSerializer
) {
    private val logger: Logger = LoggerFactory.getLogger(SQLiteTelemetryProvider::class.java)

    // Make connection nullable or lateinit
    private lateinit var connection: Connection

    // 1. Define the property first so it is ready for the init block
    private val dbPath: String = run {
        val home = System.getProperty("user.home")
        val safeAppName = appName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val dir = File(home, ".kraftadmin/$safeAppName")
        if (!dir.exists()) dir.mkdirs()
        dir.absolutePath + File.separator + "telemetry.db"
    }

    init {
        // 3. Now dbPath is guaranteed to be initialized
        migrateSchema(dbPath)
        this.connection = DriverManager.getConnection("jdbc:sqlite:$dbPath?journal_mode=WAL")
    }

    private fun migrateSchema(path: String) {
        val flyway = Flyway.configure()
            .dataSource("jdbc:sqlite:$path", null, null)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load()

        try {
            flyway.migrate()
            logger.info("✅ KraftPulse: Telemetry schema migrated successfully.")
        } catch (e: Exception) {
            logger.error("❌ KraftPulse: Schema migration failed", e)
            throw e
        }
    }

    var onEventPersisted: ((KraftTelemetryEvent) -> Unit)? = null

    fun save(event: KraftTelemetryEvent) {
        val sql = """
            INSERT OR IGNORE INTO kraft_telemetry (
                id, trace_id, type, resource, action, duration_ms, status, actor, ip_address, 
                user_agent, device_type, referer, geolocation, impact, request_details, created_at, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        executeSave(sql,
            event.id, event.traceId, event.type.name, event.resource, event.action, event.durationMs,
            event.status, serializer.toJson(event.actor), event.ipAddress, event.userAgent,
            event.deviceType, event.referer, serializer.toJson(event.geolocation),
            serializer.toJson(event.impact), serializer.toJson(event.request), event.timestamp, serializer.toJson(event)
        )
    }

    fun saveException(event: PulseExceptionEntry) {
        val sql = """
            INSERT OR IGNORE INTO kraft_exceptions (
                id, trace_id, tenant_id, user_id, exception_class, message, stack_trace, 
                stack_summary, path, method, status_code, request_headers, query_params, 
                host_name, environment, version, is_handled, metadata, created_at, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        executeSave(sql,
            event.id, event.traceId, event.tenantId, event.userId, event.exceptionClass, event.message,
            event.stackTrace, event.stackSummary, event.path, event.method, event.statusCode,
            serializer.toJson(event.requestHeaders), serializer.toJson(event.queryParams),
            event.hostName, event.environment, event.version, if(event.isHandled) 1 else 0,
            serializer.toJson(event.metadata), event.timestamp, serializer.toJson(event)
        )
    }

    fun save(event: QueryEvent) {
        val sql = """
            INSERT INTO kraft_query_events (
                id, trace_id, sql, parameters, query_type, entity_name, table_name, started_at, 
                duration_ms, rows_affected, rows_returned, is_slow, is_n_plus_one, data_source, 
                database_product, schema, tenant_id, thread_name, isolation_level, is_read_only, 
                is_batch, batch_size, transaction_id, execution_plan, error_details, created_at, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        executeSave(sql,
            event.id, event.traceId, event.sql, serializer.toJson(event.parameters), event.queryType.name,
            event.entityName, event.tableName, event.startedAt, event.durationMs, event.rowsAffected,
            event.rowsReturned, if(event.isSlowQuery) 1 else 0, if(event.isPotentialNPlusOne) 1 else 0,
            event.dataSource, event.databaseProduct, event.schema, event.tenantId, event.threadName,
            event.isolationLevel, if(event.isReadOnly) 1 else 0, if(event.isBatch) 1 else 0,
            event.batchSize, event.transactionId, event.executionPlan, serializer.toJson(event.error),
            event.startedAt, serializer.toJson(event)
        )
    }

    fun saveTask(task: KraftTaskEvent) {
        val sql = """
            INSERT OR REPLACE INTO kraft_tasks (
                id, trace_id, name, type, status, duration_ms, error_message, resource_usage, 
                node_identifier, retry_count, trigger_source, task_metadata, created_at, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        executeSave(sql,
            task.id, task.traceId, task.name, task.type.name, task.status.name, task.durationMs,
            task.errorMessage, serializer.toJson(task.resourceUsage), task.nodeIdentifier,
            task.retryCount, task.triggerSource, serializer.toJson(task.taskMetadata),
            task.createdAt, serializer.toJson(task)
        )
    }

    fun saveHttpClientEvent(event: KraftHttpClientEvent) {
        val sql = """
            INSERT OR IGNORE INTO kraft_http_client_events (
                id, trace_id, host, url, method, status_code, duration_ms, response_body_size, 
                connection_timeout_ms, error_message, created_at, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        executeSave(sql,
            event.id, event.traceId, event.host, event.url, event.method, event.statusCode,
            event.durationMs, event.responseBodySize, event.connectionTimeoutMs,
            event.errorMessage, event.createdAt, serializer.toJson(event)
        )
    }

    // --- ANALYTICS QUERIES (Moved from analytics package) ---

    fun fetchTrafficTrend(
        interval: TimeInterval,
        range: TimeRange,
        filter: TelemetryFilter,
        timeZone: ZoneId
    ): List<TrafficPoint> {
        val sqliteFormat = when (interval) {
            TimeInterval.MINUTELY -> "%Y-%m-%d %H:%M:00"
            TimeInterval.HOURLY   -> "%Y-%m-%d %H:00:00"
            TimeInterval.DAILY    -> "%Y-%m-%d 00:00:00"
        }
        val offsetStr = formatSqliteOffset(timeZone)

        val sql = """
            SELECT 
                strftime('$sqliteFormat', datetime(created_at / 1000, 'unixepoch', '$offsetStr')) as bucket,
                COUNT(*) as count
            FROM kraft_telemetry
            WHERE created_at BETWEEN ? AND ?
            ${if (filter.resource != null) "AND resource = ?" else ""}
            ${if (filter.actor != null) "AND actor = ?" else ""}
            GROUP BY bucket
            ORDER BY bucket ASC
        """.trimIndent()

        return query(sql, params = { pstmt ->
            var i = 1
            pstmt.setLong(i++, range.start.toEpochMilli())
            pstmt.setLong(i++, range.end.toEpochMilli())
            if (filter.resource != null) pstmt.setString(i++, filter.resource)
            if (filter.actor != null) pstmt.setString(i, filter.actor)
        }) { rs ->
            val points = mutableListOf<TrafficPoint>()
            while (rs.next()) {
                points.add(
                    TrafficPoint(
                        timestamp = parseSqliteDate(rs.getString("bucket"), timeZone),
                        count = rs.getInt("count")
                    )
                )
            }
            points
        }
    }

    fun fetchTopResources(limit: Int, sortBy: SortMetric): List<ResourceStats> {
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

        return query(sql, params = { it.setInt(1, limit) }) { rs ->
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

    fun fetchStatusBreakdown(filter: TelemetryFilter): Map<Int, Long> {
        val sql = """
            SELECT status, COUNT(*) as cnt
            FROM kraft_telemetry
            WHERE 1=1
            ${if (filter.resource != null) "AND resource = ?" else ""}
            ${if (filter.actor != null) "AND actor = ?" else ""}
            GROUP BY status
        """.trimIndent()

        return query(sql, params = { pstmt ->
            var i = 1
            if (filter.resource != null) pstmt.setString(i++, filter.resource)
            if (filter.actor != null) pstmt.setString(i, filter.actor)
        }) { rs ->
            val breakdown = mutableMapOf<Int, Long>()
            while (rs.next()) {
                val status = rs.getInt("status")
                if (status > 0) breakdown[status] = rs.getLong("cnt")
            }
            breakdown
        }
    }

    fun fetchLatencyPercentiles(resource: String?, range: TimeRange): LatencyReport {
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

            return query(sql, params = { pstmt ->
                val i = bindRangeAndResource(pstmt, 1)
                bindRangeAndResource(pstmt, i)
            }) { rs ->
                if (rs.next()) rs.getDouble("duration_ms") else 0.0
            }
        }

        val avgSql = "SELECT AVG(duration_ms) as avg_lat $baseWhere"
        val avg = query(avgSql, params = { pstmt -> bindRangeAndResource(pstmt, 1) }) { rs ->
            if (rs.next()) rs.getDouble("avg_lat") else 0.0
        }

        return LatencyReport(
            p50 = percentileQuery(0.50),
            p95 = percentileQuery(0.95),
            p99 = percentileQuery(0.99),
            avg = avg
        )
    }

    fun fetchRegionalDistribution(range: TimeRange): Map<String, Long> {
        val sql = """
            SELECT ip_address, COUNT(*) as cnt
            FROM kraft_telemetry
            WHERE created_at BETWEEN ? AND ?
            GROUP BY ip_address
            ORDER BY cnt DESC
            LIMIT 50
        """.trimIndent()

        return query(sql, params = { pstmt ->
            pstmt.setLong(1, range.start.toEpochMilli())
            pstmt.setLong(2, range.end.toEpochMilli())
        }) { rs ->
            val map = LinkedHashMap<String, Long>()
            while (rs.next()) {
                map[rs.getString("ip_address") ?: "unknown"] = rs.getLong("cnt")
            }
            map
        }
    }

    fun fetchSummary(range: TimeRange): AnalyticsSummary {
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

        return query(sql, params = { pstmt ->
            pstmt.setLong(1, range.start.toEpochMilli())
            pstmt.setLong(2, range.end.toEpochMilli())
        }) { rs ->
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

    // --- PIPELINE FETCH METHODS ---
    fun fetchLatestWithQueries(limit: Int): List<TelemetryWithQueries> {
        val results = mutableListOf<TelemetryWithQueries>()
        val sql = "SELECT trace_id, payload FROM kraft_telemetry ORDER BY created_at DESC LIMIT ?"

        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, limit)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val json = rs.getString("payload") ?: continue
                    val traceId = rs.getString("trace_id") ?: "unknown"
                    val event = serializer.fromJson(json, KraftTelemetryEvent::class.java)
                    val queries = fetchQueriesForTrace(traceId)
                    results.add(TelemetryWithQueries(event, queries))
                }
            }
        } catch (e: Exception) {
            System.err.println("KraftPulse: Fetch Error: ${e.message}")
        }
        return results
    }

    fun fetchQueriesForTrace(traceId: String): List<QueryEvent> {
        return fetchByTrace("kraft_query_events", traceId, QueryEvent::class.java)
    }

    fun fetchExceptionByTrace(traceId: String): PulseExceptionEntry? {
        return fetchByTrace("kraft_exceptions", traceId, PulseExceptionEntry::class.java).firstOrNull()
    }

    fun fetchHttpClientEventsForTrace(traceId: String): List<KraftHttpClientEvent> {
        return fetchByTrace("kraft_http_client_events", traceId, KraftHttpClientEvent::class.java)
    }

    fun fetchTasksForTrace(traceId: String): List<KraftTaskEvent> {
        return fetchByTrace("kraft_tasks", traceId, KraftTaskEvent::class.java)
    }

    fun fetchComprehensiveDeepDive(traceId: String): Map<String, Any?> {
        return mapOf(
            "traceId" to traceId,
            "request" to fetchByTrace("kraft_telemetry", traceId, KraftTelemetryEvent::class.java).firstOrNull(),
            "queries" to fetchQueriesForTrace(traceId),
            "exception" to fetchExceptionByTrace(traceId),
            "tasks" to fetchTasksForTrace(traceId),
            "outboundHttp" to fetchHttpClientEventsForTrace(traceId)
        )
    }

    fun <T> fetchAllPaged(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        val sql = "SELECT payload FROM $table ORDER BY created_at DESC LIMIT ? OFFSET ?"
        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, limit)
                pstmt.setInt(2, offset)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    list.add(serializer.fromJson(rs.getString("payload"), clazz))
                }
            }
        } catch (e: Exception) {
            System.err.println("KraftPulse SQLite Error (fetchAllPaged from $table): ${e.message}")
        }
        return list
    }

    // --- PIPELINE OUTBOX MANAGEMENT ---

    fun pruneOldEvents(retentionDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val tables = listOf("kraft_telemetry", "kraft_query_events", "kraft_exceptions", "kraft_tasks", "kraft_http_client_events")
        tables.forEach { table ->
            val sql = "DELETE FROM $table WHERE created_at < ? AND synced = 1"
            try {
                connection.prepareStatement(sql).use { pstmt ->
                    pstmt.setLong(1, cutoff)
                    pstmt.executeUpdate()
                }
            } catch (e: Exception) {
                System.err.println("Error cleaning table $table: ${e.message}")
            }
        }
    }

    // --- INTERNALS & ENGINE UTILS ---

    private fun <T> fetchByTrace(table: String, traceId: String, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        val sql = "SELECT payload FROM $table WHERE trace_id = ? ORDER BY created_at ASC"
        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, traceId)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    list.add(serializer.fromJson(rs.getString("payload"), clazz))
                }
            }
        } catch (e: Exception) { /* Log error */ }
        return list
    }

    private fun <T> query(sql: String, params: (PreparedStatement) -> Unit = {}, mapper: (ResultSet) -> T): T {
        return try {
            connection.prepareStatement(sql).use { pstmt ->
                params(pstmt)
                mapper(pstmt.executeQuery())
            }
        } catch (e: Exception) {
            System.err.println("KraftAdmin Analytics Query Failed: ${e.message}")
            throw e
        }
    }

    private fun executeSave(sql: String, vararg params: Any?) {
        try {
            connection.prepareStatement(sql).use { pstmt ->
                params.forEachIndexed { i, p ->
                    when (p) {
                        is String -> pstmt.setString(i + 1, p)
                        is Long -> pstmt.setLong(i + 1, p)
                        is Int -> pstmt.setInt(i + 1, p)
                        is Boolean -> pstmt.setBoolean(i + 1, p)
                        else -> pstmt.setObject(i + 1, p)
                    }
                }
                pstmt.executeUpdate()
            }
        } catch (e: Exception) {
            System.err.println("❌ SQLite Error: ${e.message}")
        }
    }

    private fun formatSqliteOffset(timeZone: ZoneId): String {
        val offset = timeZone.rules.getOffset(Instant.now())
        val totalSeconds = offset.totalSeconds
        val sign = if (totalSeconds >= 0) "+" else "-"
        val abs = Math.abs(totalSeconds)
        val hours = abs / 3600
        val minutes = (abs % 3600) / 60
        return "%s%02d:%02d".format(sign, hours, minutes)
    }

    private fun parseSqliteDate(dateStr: String, timeZone: ZoneId): Long {
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

    // --- BATCH OUTBOX MULTI-TRACE FETCHING METHODS ---

    /**
     * Efficiently pulls all database query events mapped across the target batch collection of Trace IDs.
     */
    fun fetchQueriesForTraces(traceIds: List<String>): List<QueryEvent> {
        if (traceIds.isEmpty()) return emptyList()
        return fetchByTraceIds("kraft_query_events", traceIds, QueryEvent::class.java)
    }

    /**
     * Efficiently pulls all logged exception records mapped across the target batch collection of Trace IDs.
     */
    fun fetchExceptionsForTraces(traceIds: List<String>): List<PulseExceptionEntry> {
        if (traceIds.isEmpty()) return emptyList()
        return fetchByTraceIds("kraft_exceptions", traceIds, PulseExceptionEntry::class.java)
    }

    /**
     * Efficiently pulls all asynchronous background task states mapped across the target batch collection of Trace IDs.
     */
    fun fetchTasksForTraces(traceIds: List<String>): List<KraftTaskEvent> {
        if (traceIds.isEmpty()) return emptyList()
        return fetchByTraceIds("kraft_tasks", traceIds, KraftTaskEvent::class.java)
    }

    /**
     * Efficiently pulls all outbound http client network frames mapped across the target batch collection of Trace IDs.
     */
    fun fetchHttpClientEventsForTraces(traceIds: List<String>): List<KraftHttpClientEvent> {
        if (traceIds.isEmpty()) return emptyList()
        return fetchByTraceIds("kraft_http_client_events", traceIds, KraftHttpClientEvent::class.java)
    }

    /**
     * Private generic extractor loop utilizing SQL parameter substitution blocks to parse
     * matching outbox payload variants cleanly in a single combined database query roundtrip.
     */
    private fun <T> fetchByTraceIds(table: String, traceIds: List<String>, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        // Generate ? placeholders equal to number of items in list
        val placeholders = traceIds.joinToString(",") { "?" }
        val sql = "SELECT payload FROM $table WHERE trace_id IN ($placeholders) ORDER BY created_at ASC"

        try {
            connection.prepareStatement(sql).use { pstmt ->
                traceIds.forEachIndexed { index, traceId ->
                    pstmt.setString(index + 1, traceId)
                }
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val rawPayload = rs.getString("payload")
                    if (!rawPayload.isNullOrBlank()) {
                        list.add(serializer.fromJson(rawPayload, clazz))
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("❌ KraftPulse SQLite Multi-Trace Ingestion Error (Table: $table): ${e.message}")
        }
        return list
    }

    /**
     * Retrieves all events not yet synced, including orphans (trace_id = NULL)
     * and correlated events.
     */
    fun fetchUnsyncedBatch(limit: Int): ClickHouseTelemetryBatch {
        // 1. Fetch primary telemetry events that are unsynced
        val unsyncedPrimary = fetchUnsyncedPrimary(limit)

        // 2. Extract Trace IDs from those that actually have them
        val traceIds = unsyncedPrimary.map { it.traceId }.distinct()

        // 3. Fetch correlated records for those traces + any truly orphaned records
        return ClickHouseTelemetryBatch(
            events = unsyncedPrimary,
            queries = fetchQueriesForTraces(traceIds) + fetchOrphanedQueries(),
            exceptions = fetchExceptionsForTraces(traceIds) + fetchOrphanedExceptions(),
            tasks = fetchTasksForTraces(traceIds) + fetchOrphanedTasks(),
            httpClientEvents = fetchHttpClientEventsForTraces(traceIds) + fetchOrphanedHttpEvents()
        )
    }

    private fun fetchUnsyncedPrimary(limit: Int): List<KraftTelemetryEvent> {
        val sql = "SELECT payload FROM kraft_telemetry WHERE synced = 0 ORDER BY created_at ASC LIMIT ?"
        return query(sql, params = { it.setInt(1, limit) }) { rs ->
            val list = mutableListOf<KraftTelemetryEvent>()
            while (rs.next()) list.add(serializer.fromJson(rs.getString("payload"), KraftTelemetryEvent::class.java))
            list
        }
    }

    // --- Orphaned Record Fetchers ---

    private fun fetchOrphanedQueries(): List<QueryEvent> {
        return query("SELECT payload FROM kraft_query_events WHERE synced = 0 AND trace_id IS 'outbound'") { rs ->
            val list = mutableListOf<QueryEvent>()
            while (rs.next()) list.add(serializer.fromJson(rs.getString("payload"), QueryEvent::class.java))
            list
        }
    }

    private fun fetchOrphanedExceptions(): List<PulseExceptionEntry> {
        return query("SELECT payload FROM kraft_exceptions WHERE synced = 0 AND trace_id IS 'outbound'") { rs ->
            val list = mutableListOf<PulseExceptionEntry>()
            while (rs.next()) list.add(serializer.fromJson(rs.getString("payload"), PulseExceptionEntry::class.java))
            list
        }
    }

    fun fetchOrphanedTasks(): List<KraftTaskEvent> {
        val count = query("SELECT COUNT(*) FROM kraft_tasks WHERE synced = 0") { it.next(); it.getInt(1) }
        logger.info("DEBUG: Found {} unsynced tasks in kraft_tasks", count)
        return query("SELECT payload FROM kraft_tasks WHERE synced = 0 AND trace_id IS 'outbound'") { rs ->
            val list = mutableListOf<KraftTaskEvent>()
            while (rs.next()) list.add(serializer.fromJson(rs.getString("payload"), KraftTaskEvent::class.java))
            list
        }
    }

    private fun fetchOrphanedHttpEvents(): List<KraftHttpClientEvent> {
        return query("SELECT payload FROM kraft_http_client_events WHERE synced = 0 AND trace_id IS 'outbound'") { rs ->
            val list = mutableListOf<KraftHttpClientEvent>()
            while (rs.next()) list.add(serializer.fromJson(rs.getString("payload"), KraftHttpClientEvent::class.java))
            list
        }
    }

    /**
     * Updates sync status to 1 for all records (primary + correlated children)
     * belonging to the given trace IDs. Must cover ALL FIVE tables, mirroring
     * markOrphansAsSynced's multi-table loop — otherwise correlated child
     * records (queries/tasks/exceptions/http events) for an already-synced
     * parent event become permanently unreachable.
     */
    fun markAsSynced(traceIds: List<String>) {
        if (traceIds.isEmpty()) return

        val tables = listOf(
            "kraft_telemetry",
            "kraft_query_events",
            "kraft_exceptions",
            "kraft_tasks",
            "kraft_http_client_events"
        )

        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            tables.forEach { table ->
                val placeholders = traceIds.joinToString(",") { "?" }
                val sql = "UPDATE $table SET synced = 1 WHERE trace_id IN ($placeholders) AND synced = 0"
                connection.prepareStatement(sql).use { stmt ->
                    traceIds.forEachIndexed { idx, traceId ->
                        stmt.setString(idx + 1, traceId)
                    }
                    stmt.executeUpdate()
                }
            }
            connection.commit()
            println("✅ KraftPulse Outbox State: ${traceIds.size} traces marked as [Synced] across all tables.")
        } catch (e: Exception) {
            connection.rollback()
            System.err.println("❌ KraftPulse Sync Failure: ${e.message}")
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    /**
     * Updates the sync status to 1 (synced) for all records where trace_id is NULL.
     * This should be called after successfully syncing the orphaned batch.
     */
    fun markOrphansAsSynced() {
        val tables = listOf("kraft_telemetry", "kraft_query_events", "kraft_exceptions", "kraft_tasks", "kraft_http_client_events")
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            tables.forEach { table ->
                // Ensure the table actually has the trace_id column before running
                val sql = "UPDATE $table SET synced = 1 WHERE trace_id IS NULL AND synced = 0"
                connection.createStatement().use { stmt ->
                    stmt.executeUpdate(sql)
                }
            }
            connection.commit()
            println("✅ KraftPulse Outbox State: All orphan records marked as [Synced].")
        } catch (e: Exception) {
            connection.rollback()
            System.err.println("❌ KraftPulse Orphan Sync Failure: ${e.message}")
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    fun close() = if (!connection.isClosed) connection.close() else Unit
}