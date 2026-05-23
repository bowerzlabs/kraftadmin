package interceptor

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import model.KraftTaskEvent
import model.PulseExceptionEntry
import org.slf4j.LoggerFactory
import security.AdminUserDTO
import security.SecurityProviderChain
import telementary.KraftTelemetryService
import telemetry.KraftTelemetryEvent
import telemetry.TelemetryType
import util.PulseContextHolder

class PulseTelemetryCaptor(
    private val telemetryService: KraftTelemetryService,
    private val securityChain: SecurityProviderChain
) {
    private val logger = LoggerFactory.getLogger(PulseTelemetryCaptor::class.java)

    /**
     * Captures the high-level Request metrics (Latency, User, Metadata)
     */
    fun captureRequest(request: HttpServletRequest, response: HttpServletResponse) {
        logger.info("capturing {}", request.method + " " + request.requestURI)
        val traceId = request.getAttribute("traceId") as? String ?: return
        val startTime = request.getAttribute("startTime") as? Long ?: return
        val duration = System.currentTimeMillis() - startTime

        // Prevent tracking the /error dispatch as a separate SYSTEM event
        if (request.requestURI == "/error") return

        val currentUser = securityChain.resolveCurrentUser()
        
        telemetryService.record(
            KraftTelemetryEvent(
                traceId = traceId,
                type = TelemetryType.SYSTEM,
                resource = request.requestURI,
                action = request.method,
                durationMs = duration,
                status = response.status,
                actor = currentUser?.let {
                    AdminUserDTO(it.name, it.username, it.roles, it.initials, it.avatar, it.isBridgeMode)
                },
                ipAddress = request.remoteAddr,
                userAgent = request.getHeader("User-Agent"),
                referer = request.getHeader("Referer")
            )
        )
    }

    /**
     * Captures the Exception details if a failure occurred
     */
    fun captureException(request: HttpServletRequest, response: HttpServletResponse, ex: Exception?) {
        // Deduplication check
        if (request.getAttribute("pulse_exception_captured") == true) return

        val error = ex ?: request.getAttribute("jakarta.servlet.error.exception") as? Throwable
        if (error == null && response.status < 400) return

        val context = PulseContextHolder.get()
        val entry = PulseExceptionEntry(
            traceId = context?.traceId ?: "N/A",
            tenantId = context?.tenantId,
            userId = context?.userId,
            exceptionClass = error?.javaClass?.name ?: "HTTP_${response.status}",
            message = error?.message ?: "Handled Error",
            stackTrace = error?.stackTraceToString() ?: "N/A",
            path = request.requestURI,
            method = request.method,
            statusCode = response.status,
            metadata = mapOf("params" to request.parameterMap.mapValues { it.value.toList() })
        )

        telemetryService.recordException(entry)
        request.setAttribute("pulse_exception_captured", true)
        logger.info("Captured error for trace [${entry.traceId}]")
    }


    /**
     * Tracks the lifecycle of background executions (Scheduled crons, Async jobs, listeners)
     */
    fun recordTask(task: KraftTaskEvent) {
        logger.info("logging task event $task")
        telemetryService.recordTaskEvent(task)
        logger.info("Task Logged [${task.type}] - ${task.name} (${task.status}) in ${task.durationMs}ms")
    }

    fun recordBackgroundException(entry: PulseExceptionEntry) {
        telemetryService.recordException(entry)
        logger.info("Captured background exception for trace [${entry.traceId}] from task pipeline.")
    }

    fun recordOutboundHttp(event: model.KraftHttpClientEvent) {
        // Forward directly to your commonStore persistence layer through the telemetryService contract
        telemetryService.recordHttpClientEvent(event)
        logger.info("Logged Outbound HTTP [${event.method}] -> ${event.url} (${event.statusCode}) in ${event.durationMs}ms")
    }

}