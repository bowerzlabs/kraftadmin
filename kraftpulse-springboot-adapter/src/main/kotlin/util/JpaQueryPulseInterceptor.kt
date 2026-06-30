//package util
//
//import interceptors.PulseContextProvider
//import interceptors.QueryPulseInterceptor
//import model.PulseContext
//import model.QueryError
//import model.QueryEvent
//import model.QueryStatus
//import model.QueryType
//import net.ttddyy.dsproxy.ExecutionInfo
//import net.ttddyy.dsproxy.QueryInfo
//import net.ttddyy.dsproxy.listener.QueryExecutionListener
//import org.springframework.stereotype.Component
//import java.sql.SQLException
//import java.util.concurrent.ConcurrentHashMap
//import kotlin.concurrent.thread
//
//@Component
//class JpaPulseQueryListener(
//    private val interceptor: QueryPulseInterceptor,
//    private val contextProvider: PulseContextProvider,
//    // ✅ Configurable — don't hardcode the slow-query threshold
//    private val slowQueryThresholdMs: Long = 500,
//    // ✅ N+1 detection window — how many identical patterns within
//    // a single trace before we flag it
//    private val nPlusOneThreshold: Int = 5
//) : QueryExecutionListener {
//
//    // Per-trace pattern counters for N+1 detection. Cleared when the
//    // request completes — wire a cleanup hook from your request filter,
//    // or use a time-based eviction (see note below).
//    private val patternCounters = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
//
//    override fun beforeQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
//        // No-op — dsproxy's ExecutionInfo already carries elapsed time
//    }
//
//    override fun afterQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
//        val context = contextProvider.currentContext() ?: PulseContext.SYSTEM_DEFAULT
//
////        val events = queryInfoList.map { query ->
////            val sql = query.query
////            val operation = determineOperation(sql)
////            val durationMs = execInfo.elapsedTime
////
////            QueryEvent(
////                traceId = context.traceId ?: "system",
////                sql = sql,
////                queryType = QueryType.valueOf(operation),
////                tableName = extractTableName(sql),
////                durationMs = durationMs,
////                rowsAffected = extractRowsAffected(execInfo).toInt(),
////                rowsReturned = extractRowsReturned(execInfo).toInt(),
////                isSlowQuery = durationMs >= slowQueryThresholdMs,
////                isPotentialNPlusOne = false, // determined below, after counting
////                dataSource = execInfo.dataSourceName,
////                startedAt = System.currentTimeMillis() - durationMs
////            )
////        }
//
//        val events = queryInfoList.map { query ->
//            val sql = query.query
//            val durationMs = execInfo.elapsedTime
//
//            // Map parameters from QueryInfo
//            val params = query.parametersList.flatMap { it.parameters }.map { it.value?.toString() }
//
//            QueryEvent(
//                traceId = context.traceId ?: "system",
//                sql = sql,
//                parameters = params,
//                queryType = QueryType.valueOf(determineOperation(sql)),
//                tableName = extractTableName(sql),
//                durationMs = durationMs,
//                rowsAffected = extractRowsAffected(execInfo).toInt(),
//                isSlowQuery = durationMs >= slowQueryThresholdMs,
//                startedAt = System.currentTimeMillis() - durationMs,
//
//                // --- Enhanced Context ---
//                tenantId = context.tenantId,
//                threadName = thread.name,
//                isolationLevel = connectionInfo.metaData.let {
//                    try { it.transactionIsolation.toString() } catch(e: Exception) { null }
//                },
//                isReadOnly = try { connectionInfo.metaData.isReadOnly } catch(e: Exception) { false },
//                dataSource = execInfo.dataSourceName,
//                databaseProduct = try { connectionInfo.metaData.databaseProductName } catch(e: Exception) { null },
//
//                // Handle Errors
//                error = execInfo.throwable?.let {
//                    QueryError(
//                        sqlState = (it as? SQLException)?.sqlState,
//                        errorCode = (it as? SQLException)?.errorCode ?: 0,
//                        message = it.message ?: "Unknown Error",
//                        exceptionClass = it.javaClass.name
//                    )
//                }
//            )
//        }
//
//        // ✅ Hand off to the interceptor — this is the line that was missing entirely
//        if (events.size > 1) {
//            interceptor.onBatch(context, events)
//        } else if (events.isNotEmpty()) {
//            interceptor.onQuery(context, events.first())
//        }
//
//        // ✅ Slow query detection — call the dedicated hook in addition to onQuery
//        events.filter { it.isSlowQuery }.forEach { interceptor.onSlowQuery(context, it) }
//
//        // ✅ N+1 detection — track normalized query patterns per trace
//        events.forEach { event -> checkNPlusOne(context, event) }
//    }
//
//    /**
//     * Detects N+1 by normalizing the SQL (stripping literal values) and
//     * counting repeated patterns within the same trace.
//     */
//    private fun checkNPlusOne(context: PulseContext, event: QueryEvent) {
//        val traceId = context.traceId ?: return
//        val pattern = normalizeForPatternMatching(event.sql ?: return)
//
//        val traceCounters = patternCounters.computeIfAbsent(traceId) { ConcurrentHashMap() }
//        val newCount = traceCounters.merge(pattern, 1, Int::plus) ?: 1
//
//        if (newCount == nPlusOneThreshold) {
//            // Fire exactly once when crossing the threshold, not on every subsequent occurrence
//            interceptor.onNPlusOneDetected(context, pattern, newCount)
//        }
//    }
//
//    /** Strips literal values so "WHERE id = 5" and "WHERE id = 9" count as the same pattern. */
//    private fun normalizeForPatternMatching(sql: String): String {
//        return sql
//            .replace(Regex("\\b\\d+\\b"), "?")           // numeric literals
//            .replace(Regex("'[^']*'"), "'?'")             // string literals
//            .replace(Regex("\\s+"), " ")                  // collapse whitespace
//            .trim()
//    }
//
//    /** Call this from your request-completion hook to prevent unbounded memory growth. */
//    fun clearTraceCounters(traceId: String) {
//        patternCounters.remove(traceId)
//    }
//
//    private fun mapStatus(execInfo: ExecutionInfo): QueryStatus = when {
//        execInfo.isSuccess -> QueryStatus.SUCCESS
//        execInfo.throwable?.message?.contains("timeout", ignoreCase = true) == true -> QueryStatus.TIMEOUT
//        else -> QueryStatus.DATABASE_ERROR
//    }
//
//    private fun extractRowsAffected(execInfo: ExecutionInfo): Long {
//        val result = execInfo.result
//        return if (result is Number) result.toLong() else 0L
//    }
//
//    private fun extractRowsReturned(execInfo: ExecutionInfo): Long {
//        // dsproxy doesn't directly expose row count for SELECTs without
//        // wrapping the ResultSet — return 0 as a safe default unless you
//        // add a ResultSetProxyLogicFactory to count rows explicitly.
//        return 0L
//    }
//
//    private fun extractTableName(sql: String): String? {
//        // Lightweight heuristic — good enough for grouping/dashboards,
//        // not meant to be a full SQL parser
//        val normalized = sql.trim().uppercase()
//        val regex = when {
//            normalized.startsWith("SELECT") -> Regex("FROM\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
//            normalized.startsWith("INSERT") -> Regex("INTO\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
//            normalized.startsWith("UPDATE") -> Regex("UPDATE\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
//            normalized.startsWith("DELETE") -> Regex("FROM\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
//            else -> return null
//        }
//        return regex.find(sql)?.groupValues?.get(1)?.trim('"')
//    }
//
//    private fun determineOperation(sql: String): String {
//        val normalized = sql.trimStart().take(10).uppercase()
//        return when {
//            normalized.startsWith("SELECT") -> "SELECT"
//            normalized.startsWith("INSERT") -> "INSERT"
//            normalized.startsWith("UPDATE") -> "UPDATE"
//            normalized.startsWith("DELETE") -> "DELETE"
//            normalized.startsWith("CALL") -> "PROCEDURE"
//            else -> "SQL_EXEC"
//        }
//    }
//}

package util

import interceptors.PulseContextProvider
import interceptors.QueryPulseInterceptor
import model.*
import net.ttddyy.dsproxy.ExecutionInfo
import net.ttddyy.dsproxy.QueryInfo
import net.ttddyy.dsproxy.listener.QueryExecutionListener
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

@Component
class JpaPulseQueryListener(
    private val interceptor: QueryPulseInterceptor,
    private val contextProvider: PulseContextProvider,
    private val slowQueryThresholdMs: Long = 500,
    private val nPlusOneThreshold: Int = 5
) : QueryExecutionListener {

    private val patternCounters = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    override fun beforeQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {}

    override fun afterQuery(execInfo: ExecutionInfo, queryInfoList: MutableList<QueryInfo>) {
        val context = contextProvider.currentContext() ?: PulseContext.SYSTEM_DEFAULT
        val threadName = Thread.currentThread().name
        val metaData = try { execInfo.statement?.connection?.metaData } catch (e: Exception) { null }

        val events = queryInfoList.map { query ->
            val sql = query.query
            val durationMs = execInfo.elapsedTime

            val params = mutableListOf<String?>()
            for (parameterSet in query.parametersList) {
                for (param in parameterSet) {
                    params.add(param?.toString())
                }
            }

            QueryEvent(
                traceId = context.traceId ?: "system",
                sql = sql,
                parameters = params,
                queryType = QueryType.valueOf(determineOperation(sql)),
                tableName = extractTableName(sql),
                durationMs = durationMs,
                rowsAffected = extractRowsAffected(execInfo).toInt(),
                isSlowQuery = durationMs >= slowQueryThresholdMs,
                startedAt = System.currentTimeMillis() - durationMs,

                tenantId = context.tenantId,
                threadName = threadName,
                isolationLevel = execInfo.isolationLevel.toString(),
                isReadOnly = try { metaData?.isReadOnly ?: false } catch (e: Exception) { false },
                dataSource = execInfo.dataSourceName,
                databaseProduct = try { metaData?.databaseProductName } catch (e: Exception) { null },

                error = execInfo.throwable?.let {
                    QueryError(
                        sqlState = (it as? SQLException)?.sqlState,
                        errorCode = (it as? SQLException)?.errorCode ?: 0,
                        message = it.message ?: "Unknown Error",
                        exceptionClass = it.javaClass.name
                    )
                }
            )
        }

        if (events.size > 1) {
            interceptor.onBatch(context, events.map { it.copy(isBatch = true, batchSize = events.size) })
        } else if (events.isNotEmpty()) {
            interceptor.onQuery(context, events.first())
        }

        events.filter { it.isSlowQuery }.forEach { interceptor.onSlowQuery(context, it) }
        events.forEach { checkNPlusOne(context, it) }
    }

    private fun checkNPlusOne(context: PulseContext, event: QueryEvent) {
        val traceId = context.traceId ?: return
        val pattern = normalizeForPatternMatching(event.sql)
        val traceCounters = patternCounters.computeIfAbsent(traceId) { ConcurrentHashMap() }
        val newCount = traceCounters.merge(pattern, 1, Int::plus) ?: 1

        if (newCount == nPlusOneThreshold) {
            interceptor.onNPlusOneDetected(context, pattern, newCount)
        }
    }

    private fun normalizeForPatternMatching(sql: String): String = sql
        .replace(Regex("\\b\\d+\\b"), "?")
        .replace(Regex("'[^']*'"), "'?'")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun clearTraceCounters(traceId: String) = patternCounters.remove(traceId)

    private fun extractRowsAffected(execInfo: ExecutionInfo): Long =
        (execInfo.result as? Number)?.toLong() ?: 0L

    private fun extractTableName(sql: String): String? {
        val normalized = sql.trim().uppercase()
        val regex = when {
            normalized.startsWith("SELECT") -> Regex("FROM\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
            normalized.startsWith("INSERT") -> Regex("INTO\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
            normalized.startsWith("UPDATE") -> Regex("UPDATE\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
            normalized.startsWith("DELETE") -> Regex("FROM\\s+([\\w.\"]+)", RegexOption.IGNORE_CASE)
            else -> return null
        }
        return regex.find(sql)?.groupValues?.get(1)?.trim('"')
    }

    private fun determineOperation(sql: String): String {
        val normalized = sql.trimStart().take(10).uppercase()
        return when {
            normalized.startsWith("SELECT") -> "SELECT"
            normalized.startsWith("INSERT") -> "INSERT"
            normalized.startsWith("UPDATE") -> "UPDATE"
            normalized.startsWith("DELETE") -> "DELETE"
            normalized.startsWith("CALL") -> "CALL"
            else -> "UNKNOWN"
        }
    }
}