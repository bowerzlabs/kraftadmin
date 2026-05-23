//package util
//
////import interceptors.QueryPulseInterceptor
////import model.PulseContext
////import model.QueryEvent
////import org.springframework.stereotype.Component
////
////@Component
////class DefaultQueryPulseInterceptor : QueryPulseInterceptor {
////    override fun onQuery(context: PulseContext, event: QueryEvent) {
////        // This is the "Zero-Noise" handoff point
////        println("📊 [KraftPulse] ${event.operation} | ${event.durationMs}ms | ${event.statement}")
////    }
////}
//
//import interceptors.QueryPulseInterceptor
//import model.PulseContext
//import model.QueryEvent
//import telemetry.ErrorDetails
//import telemetry.EventImpact
//import telemetry.EventStatus
//import telemetry.KraftTelemetryEvent
//import telemetry.TelemetryType
//import java.time.LocalDateTime
//import java.util.UUID
//import kotlin.collections.buildMap
//
///**
// * Bridges the low-level [QueryPulseInterceptor] into KraftAdmin's unified
// * [KraftTelemetryEvent] stream.
// *
// * Register your [TelemetryEmitter] and this class handles translation.
// * This is the seam between raw query data and the KraftAdmin dashboard.
// */
//class QueryTelemetryBridge(
//    private val emitter: TelemetryEmitter
//) : QueryPulseInterceptor {
//
//    override fun onQuery(context: PulseContext, event: QueryEvent) {
//        val telemetry = KraftTelemetryEvent(
////            traceId  = context.traceId,
//            type = TelemetryType.DATA_ACCESS,
//            resource = event.entityName ?: event.tableName ?: "unknown",
//            action = event.queryType.name,
//            durationMs = event.durationMs,
//            id = UUID.randomUUID().toString(),
//            timestamp = 0L,
//            status = 200,
//            actor = null,
//            ipAddress = "127.0.0.1",
//            userAgent = "mozilla",
//            deviceType = "device",
//            referer = "",
//            geolocation = null,
////            status   = if (event.error == null) EventStatus.SUCCESS else EventStatus.SERVER_ERROR,
////            actor    = context.actor,
////            request  = context.request,
////            error    = event.error?.let {
////                ErrorDetails(
////                    exceptionClass = it.exceptionClass,
////                    message = it.message,
////                    stackSummary = it.message   // stack isn't available at JDBC level
////                )
////            },
////            impact   = EventImpact(
////                rowsAffected = event.rowsAffected,
////                queryCount = 1,
////                isCacheHit = false
////            ),
////            tags = buildMap {
////                put("sql.type", event.queryType.name)
////                put("datasource", event.dataSource)
////                event.tableName?.let  { put("table", it) }
////                event.schema?.let     { put("schema", it) }
////                if (event.isSlowQuery)         put("slow_query", "true")
////                if (event.isPotentialNPlusOne) put("n_plus_one", "true")
////                context.tenantId?.let { put("tenant_id", it) }
//////                context.environment.let { put("env", it) }
////                putAll(context.tags)
////            }
//        )
//
//        emitter.emit(telemetry)
//    }
//
//    override fun onSlowQuery(context: PulseContext, event: QueryEvent) {
//        // Already included in onQuery via tags — override here for alerting hooks
//        emitter.onAlert(AlertType.SLOW_QUERY, context, "Query took ${event.durationMs}ms: ${event.sql.take(100)}")
//    }
//
//    override fun onNPlusOneDetected(context: PulseContext, pattern: String, occurrences: Int) {
//        emitter.onAlert(AlertType.N_PLUS_ONE, context, "N+1 detected ($occurrences hits): $pattern")
//    }
//}
//
//// ---------------------------------------------------------------------------
//// Emitter interface — implement this to plug in your storage/transport
//// ---------------------------------------------------------------------------
//
//interface TelemetryEmitter {
//    fun emit(event: KraftTelemetryEvent)
//    fun onAlert(type: AlertType, context: PulseContext, message: String) {}
//}
//
//enum class AlertType {
//    SLOW_QUERY,
//    N_PLUS_ONE,
//    HIGH_ERROR_RATE,
//    HIGH_CPU,
//    QUEUE_BACKLOG
//}

package util

import interceptors.PulseContextProvider
import model.PulseContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The primary provider for KraftPulse.
 * Resolves the current execution context for telemetry attribution.
 */
@Component
//class DefaultPulseContextProvider : PulseContextProvider {
//
//    override fun currentContext(): PulseContext? {
//        // 1. In Phase 2, we will add Reactor/ThreadLocal lookups here.
//        // 2. For Phase 1 (The Sniffer), we return the System fallback.
//        return PulseContext.SYSTEM_DEFAULT
//    }
//}
class DefaultPulseContextProvider : PulseContextProvider {
    override fun currentContext(): PulseContext? {
        return PulseContextHolder.get()
    }
}

//@Component
//class DefaultPulseContextProvider : PulseContextProvider {
//
//    override fun currentContext(): PulseContext? {
//        // 1. Try to get the username from Spring Security
//        val auth = SecurityContextHolder.getContext().authentication
//        val username = auth?.name ?: "anonymous"
//
//        // 2. Return a context that links the query to the user
//        return PulseContext(
//            traceId = UUID.randomUUID().toString(), // Or pull from a Request Header
//            tenantId = auth?.name ?: "anonymous",
//            source = "web-request"
//        )
//    }
//}

