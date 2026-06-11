package telemetry

import analytics.TelemetryWithQueries
import json.KraftJsonSerializer
import model.KraftHttpClientEvent
import model.KraftTaskEvent
import model.PulseExceptionEntry
import model.QueryEvent
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class SQLiteTelemetryProvider(
    private val appName: String = "default-app",
    val serializer: KraftJsonSerializer
) {
    private val dbPath: String = run {
        val home = System.getProperty("user.home")
        val safeAppName = appName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val dir = File(home, ".kraftadmin/$safeAppName")
        if (!dir.exists()) dir.mkdirs()
        dir.absolutePath + File.separator + "telemetry.db"
    }

    var onEventPersisted: ((KraftTelemetryEvent) -> Unit)? = null
    val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath?journal_mode=WAL")

    init {
        try {
            connection.createStatement().use { statement ->
                // 1. Core Telemetry (Appended synced column)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS kraft_telemetry (
                        id TEXT PRIMARY KEY,
                        trace_id TEXT,
                        type TEXT,
                        resource TEXT,
                        action TEXT,
                        duration_ms INTEGER,
                        status INTEGER,
                        actor TEXT,
                        ip_address TEXT,
                        user_agent TEXT,
                        referer TEXT,
                        created_at INTEGER,
                        payload TEXT,
                        synced INTEGER DEFAULT 0
                    )
                """)

                // 2. SQL Query Events (Appended synced column)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS kraft_query_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        trace_id TEXT NOT NULL,
                        sql TEXT,
                        query_type TEXT,
                        entity_name TEXT,
                        table_name TEXT,
                        duration_ms INTEGER,
                        rows_returned INTEGER,
                        rows_affected INTEGER,
                        is_slow BOOLEAN,
                        is_n_plus_one BOOLEAN,
                        data_source TEXT,
                        created_at INTEGER,
                        payload TEXT,
                        synced INTEGER DEFAULT 0
                    )
                """)

                // 3. Exception Storage (Appended synced column)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS kraft_exceptions (
                        id TEXT PRIMARY KEY,
                        trace_id TEXT NOT NULL,
                        exception_class TEXT,
                        message TEXT,
                        stack_trace TEXT,
                        path TEXT,
                        method TEXT,
                        status_code INTEGER,
                        created_at INTEGER,
                        payload TEXT,
                        synced INTEGER DEFAULT 0
                    )
                """)

                // 4. Tasks & Jobs (Appended synced column)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS kraft_tasks (
                        id TEXT PRIMARY KEY,
                        trace_id TEXT NOT NULL,
                        name TEXT,
                        type TEXT,
                        status TEXT,
                        duration_ms INTEGER,
                        error_message TEXT,
                        created_at INTEGER,
                        payload TEXT,
                        synced INTEGER DEFAULT 0
                    )
                """)

                // 5. Outbound HTTP Client Requests Table (Appended synced column)
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS kraft_http_client_events (
                        id TEXT PRIMARY KEY,
                        trace_id TEXT NOT NULL,
                        url TEXT,
                        method TEXT,
                        status_code INTEGER,
                        duration_ms INTEGER,
                        created_at INTEGER,
                        payload TEXT,
                        synced INTEGER DEFAULT 0
                    )
                """)

                // 6. Performance Optimization & State Filtering Indices
                statement.execute("CREATE INDEX IF NOT EXISTS idx_http_client_trace ON kraft_http_client_events(trace_id)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_http_client_created ON kraft_http_client_events(created_at, synced)")

                statement.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_trace ON kraft_telemetry(trace_id)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_sync ON kraft_telemetry(synced, created_at)")

                statement.execute("CREATE INDEX IF NOT EXISTS idx_query_trace ON kraft_query_events(trace_id)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_query_sync ON kraft_query_events(synced, created_at)")

                statement.execute("CREATE INDEX IF NOT EXISTS idx_exc_trace ON kraft_exceptions(trace_id)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_task_trace ON kraft_tasks(trace_id)")

                println("✅ KraftPulse: Non-Destructive State Outbox Schema Synchronized.")
            }
        } catch (e: Exception) {
            System.err.println("❌ KraftPulse: Schema Error: ${e.message}")
        }
    }

    // --- SAVE METHODS ---

    fun save(event: KraftTelemetryEvent) {
        val sql = "INSERT OR IGNORE INTO kraft_telemetry (id, trace_id, type, resource, action, duration_ms, status, actor, ip_address, user_agent, referer, created_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        executeSave(sql, event.id, event.traceId, event.type.name, event.resource, event.action, event.durationMs, event.status, serializer.toJson(event.actor), event.ipAddress, event.userAgent, event.referer, event.timestamp, serializer.toJson(event))
    }

    fun saveException(event: PulseExceptionEntry) {
        val sql = "INSERT OR IGNORE INTO kraft_exceptions (id, trace_id, exception_class, message, stack_trace, path, method, status_code, created_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        executeSave(sql, event.id, event.traceId, event.exceptionClass, event.message, event.stackTrace, event.path, event.method, event.statusCode, event.timestamp, serializer.toJson(event))
    }

    fun save(event: QueryEvent) {
        val sql = "INSERT INTO kraft_query_events (trace_id, sql, query_type, entity_name, table_name, duration_ms, rows_returned, rows_affected, is_slow, is_n_plus_one, data_source, created_at, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        executeSave(sql, event.traceId, event.sql, event.queryType.name, event.entityName, event.tableName, event.durationMs, event.rowsReturned, event.rowsAffected, event.isSlowQuery, event.isPotentialNPlusOne, event.dataSource, event.startedAt, serializer.toJson(event))
    }

    fun saveTask(task: KraftTaskEvent) {
        val sql = """
            INSERT OR REPLACE INTO kraft_tasks 
            (id, trace_id, name, type, status, duration_ms, error_message, created_at, payload) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        executeSave(sql, task.id, task.traceId, task.name, task.type.name, task.status.name, task.durationMs, task.errorMessage, task.createdAt, serializer.toJson(task))
    }

    fun saveHttpClientEvent(event: KraftHttpClientEvent) {
        val sql = """
            INSERT OR IGNORE INTO kraft_http_client_events 
            (id, trace_id, url, method, status_code, duration_ms, created_at, payload) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        executeSave(sql, event.id, event.traceId, event.url, event.method, event.statusCode, event.durationMs, event.createdAt, serializer.toJson(event))
    }

    // --- FAULTPROOF FETCH METHODS (Polls Unsynced State `synced = 0`) ---

    fun fetchBatch(limit: Int): List<KraftTelemetryEvent> {
        val events = mutableListOf<KraftTelemetryEvent>()
        val sql = "SELECT payload FROM kraft_telemetry WHERE synced = 0 ORDER BY created_at ASC LIMIT ?"

        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, limit)
                val rs = pstmt.executeQuery()
                while (rs.next()) {
                    val json = rs.getString("payload")
                    events.add(serializer.fromJson(json, KraftTelemetryEvent::class.java))
                }
            }
        } catch (e: Exception) {
            System.err.println("KraftAdmin SQLite Error (FetchBatch): ${e.message}")
        }
        return events
    }

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
            System.err.println(" KraftPulse: Fetch Error: ${e.message}")
        }
        return results
    }

    fun fetchQueriesForTrace(traceId: String): List<QueryEvent> {
        return fetchByTrace("kraft_query_events", traceId, QueryEvent::class.java)
    }

    fun fetchExceptionByTrace(traceId: String): PulseExceptionEntry? {
        return fetchByTrace("kraft_exceptions", traceId, PulseExceptionEntry::class.java).firstOrNull()
    }

    fun fetchDeepDive(traceId: String): Map<String, Any?> {
        return mapOf(
            "traceId" to traceId,
            "request" to fetchByTrace("kraft_telemetry", traceId, KraftTelemetryEvent::class.java).firstOrNull(),
            "queries" to fetchQueriesForTrace(traceId),
            "exception" to fetchExceptionByTrace(traceId)
        )
    }

    // --- IDEMPOTENT PIPELINE STATE TRANSITION ---

    /**
     * Updates the status of records across all operational tables to synced.
     * This protects against cascading relational splits if network drops occur.
     */
    fun markAsSynced(traceIds: List<String>) {
        if (traceIds.isEmpty()) return
        val placeholders = traceIds.joinToString(",") { "?" }

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
                val sql = "UPDATE $table SET synced = 1 WHERE trace_id IN ($placeholders)"
                connection.prepareStatement(sql).use { pstmt ->
                    traceIds.forEachIndexed { index, traceId ->
                        pstmt.setString(index + 1, traceId)
                    }
                    pstmt.executeUpdate()
                }
            }
            connection.commit()
            println("⚙️ KraftPulse Outbox State: Flipped state to [Synced] for ${traceIds.size} operational traces.")
        } catch (e: Exception) {
            connection.rollback()
            System.err.println("❌ KraftPulse State Transition Failure: Reverting batch sync flag: ${e.message}")
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    // --- JANITOR PURGE MAINTENANCE (Only deletes historical data that has been successfully pushed) ---

    fun pruneOldEvents(retentionDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val tables = listOf(
            "kraft_telemetry",
            "kraft_query_events",
            "kraft_exceptions",
            "kraft_tasks",
            "kraft_http_client_events"
        )

        tables.forEach { table ->
            // CRITICAL CHECK: Only deletes records that are older than the retention window AND verified pushed (synced = 1)
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

    // --- PRIVATE UTILS ---

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

    fun <T> fetchAllPaged(table: String, limit: Int, offset: Int, clazz: Class<T>): List<T> {
        val list = mutableListOf<T>()
        val sql = "SELECT payload FROM $table ORDER BY created_at DESC LIMIT ? OFFSET ?"
        try {
            connection.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, limit)
                val pstmtInt = pstmt
                pstmtInt.setInt(2, offset)
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

    fun close() = if (!connection.isClosed) connection.close() else Unit
}