package config

import analytics.AnalyticsProvider
import interceptors.QueryPulseInterceptor
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import model.PulseContext
import model.QueryEvent
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import telemetry.RequestDetails
import util.PulseContextHolder
import java.util.UUID
import javax.sql.DataSource
import kotlin.collections.forEach

// ---------------------------------------------------------------------------
// Configuration Properties
// ---------------------------------------------------------------------------

@ConfigurationProperties(prefix = "kraft.pulse.query")
data class PulseQueryProperties(
    val slowQueryThresholdMs: Long = 500L,
    val nPlusOneThreshold: Int = 5,
    val captureParameters: Boolean = true,
    val dataSourceName: String = "primary",
    val enabled: Boolean = true
)

// ---------------------------------------------------------------------------
// Auto-Configuration
// ---------------------------------------------------------------------------

@AutoConfiguration
@EnableConfigurationProperties(PulseQueryProperties::class)
@ConditionalOnProperty(prefix = "kraft.pulse", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class PulseAutoConfiguration {

//    @Bean
//    @ConditionalOnMissingBean
//    fun pulseQueryConfig(props: PulseQueryProperties) = PulseQueryConfig(
//        slowQueryThresholdMs = props.slowQueryThresholdMs,
//        nPlusOneThreshold    = props.nPlusOneThreshold,
//        captureParameters    = props.captureParameters,
//        dataSourceName       = props.dataSourceName,
//        enabled              = props.enabled
//    )

    /**
     * Wraps the primary [DataSource] with [PulseDataSourceProxy] so all
     * JDBC calls are automatically intercepted.
     *
     * @Primary ensures Spring prefers this bean over the raw DataSource.
     */
//    @Bean
//    @Primary
//    @ConditionalOnBean(DataSource::class)
//    fun pulseDataSource(
//        dataSource: DataSource,
//        interceptors: List<QueryPulseInterceptor>,
//        config: PulseQueryConfig
//    ): DataSource {
//        if (!config.enabled || interceptors.isEmpty()) return dataSource
//        return PulseDataSourceProxy(dataSource, interceptors, config)
//    }

    /**
     * Servlet filter that binds a [PulseContext] to the current thread at
     * the start of every HTTP request and clears it when the response is sent.
     */
    @Bean
    @ConditionalOnMissingBean(PulseRequestFilter::class)
    fun pulseRequestFilter(): PulseRequestFilter = PulseRequestFilter()
}

// ---------------------------------------------------------------------------
// Request Filter — binds PulseContext for the lifetime of each HTTP request
// ---------------------------------------------------------------------------

@Component
@Order(1)          // Run before everything else
class PulseRequestFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val http = request as? HttpServletRequest

        val traceId = http?.getHeader("X-Trace-Id")
            ?: http?.getHeader("X-Request-Id")
            ?: UUID.randomUUID().toString()

        val context = PulseContext(
            traceId   = traceId,
            tenantId  = http?.getHeader("X-Tenant-Id"),
//            request   = http?.toRequestDetails()
        )

        PulseContextHolder.set(context)
        try {
            chain.doFilter(request, response)
        } finally {
            PulseContextHolder.clear()
        }
    }

    private fun HttpServletRequest.toRequestDetails() = RequestDetails(
        method = method,
        path = requestURI,
        fullUrl = requestURL.toString() + (queryString?.let { "?$it" } ?: ""),
        ipAddress = getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: remoteAddr,
        userAgent = getHeader("User-Agent"),
        referer = getHeader("Referer"),
        origin = getHeader("Origin"),
        // UA parsing is left to an optional dependency (e.g. ua-parser-java)
        deviceType = null,
        browser = null,
        os = null,
        controller = null,    // Filled by Spring MVC HandlerInterceptor later
        handlerMethod = null,
        routePattern = null,
        locale = locale.toString(),
        timezone = getHeader("X-Timezone")
    )
}

// ---------------------------------------------------------------------------
// Default no-op interceptor — replace with your storage implementation
// ---------------------------------------------------------------------------

/**
 * A logging-only [QueryPulseInterceptor] that is registered automatically
 * when no other implementation is found. Replace it with your own bean
 * that writes to your storage backend.
 */
@Component
@ConditionalOnMissingBean(QueryPulseInterceptor::class)
class LoggingQueryPulseInterceptor : QueryPulseInterceptor {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    override fun onQuery(context: PulseContext, event: model.QueryEvent) {
        log.debug(
            "[Pulse] trace={} sql='{}' type={} duration={}ms rows={}",
            context.traceId,
            event.sql.take(120),
            event.queryType,
            event.durationMs,
            event.rowsAffected
        )
    }

    override fun onSlowQuery(context: PulseContext, event: model.QueryEvent) {
        log.warn(
            "[Pulse:SLOW] trace={} duration={}ms sql='{}'",
            context.traceId, event.durationMs, event.sql.take(200)
        )
    }

    override fun onNPlusOneDetected(context: PulseContext, pattern: String, occurrences: Int) {
        log.warn(
            "[Pulse:N+1] trace={} occurrences={} pattern='{}'",
            context.traceId, occurrences, pattern.take(200)
        )
    }
}


/**
 * A non-blocking implementation of [QueryPulseInterceptor] that persists
 * telemetry data to the [AnalyticsProvider] using a background thread pool.
 */
@Component
class AsyncQueryPulseInterceptor(
    private val analyticsProvider: AnalyticsProvider
) : QueryPulseInterceptor {

    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

//    @Async("pulseTaskExecutor")
//    override fun onQuery(context: PulseContext, event: QueryEvent) {
//        // Ensure the traceId from the request context is attached to the event
//        val enrichedEvent = event.copy(traceId = context.traceId ?: "system")
//        try {
//            analyticsProvider.save(enrichedEvent)
//        } catch (e: Exception) {
//            log.error("[Pulse] Failed to persist query telemetry asynchronously", e)
//        }
//    }

    @Async("pulseTaskExecutor")
    override fun onQuery(context: PulseContext, event: QueryEvent) {
        // 1. Use the traceId ALREADY captured in the context
        // 2. Fallback to "system" only if context is missing it
        val resolvedTraceId = context.traceId ?: "system"

        val enrichedEvent = event.copy(traceId = resolvedTraceId)

        log.debug("DEBUG: Persistence started on thread: ${Thread.currentThread().name} for trace: $resolvedTraceId")

        try {
            analyticsProvider.save(enrichedEvent)
        } catch (e: Exception) {
            log.error("[Pulse] Failed to persist query telemetry", e)
        }
    }

    @Async("pulseTaskExecutor")
    override fun onBatch(context: PulseContext, events: List<QueryEvent>) {
        val traceId = context.traceId ?: "system"
        events.forEach { event ->
            analyticsProvider.save(event.copy(traceId = traceId))
        }
    }

    override fun onSlowQuery(context: PulseContext, event: QueryEvent) {
        log.warn("[Pulse:SLOW] trace={} duration={}ms sql='{}'",
            context.traceId, event.durationMs, event.sql.take(200))
    }

    override fun onNPlusOneDetected(context: PulseContext, pattern: String, occurrences: Int) {
        log.warn("[Pulse:N+1] trace={} occurrences={} pattern='{}'",
            context.traceId, occurrences, pattern.take(200))
    }
}