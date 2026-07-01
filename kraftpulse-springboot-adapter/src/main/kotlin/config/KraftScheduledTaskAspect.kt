package config

import interceptor.PulseTelemetryCaptor
import model.KraftTaskEvent
import model.KraftTaskStatus
import model.KraftTaskType
import model.PulseExceptionEntry
import org.springframework.stereotype.Component
import util.PulseContextHolder
import model.PulseContext
import model.ResourceUsage
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.util.UUID

@Aspect
//@Component
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftScheduledTaskAspect(
    private val captor: PulseTelemetryCaptor
) {

    // ✅ Centralized self-exclusion — never track KraftPulse's own scheduled jobs
    private val excludedPackagePrefixes = setOf(
        "telemetry.",
        "analytics.",
        "interceptor.",
        "config."
    )

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    fun profileScheduledTask(joinPoint: ProceedingJoinPoint): Any? {
        val targetClassName = joinPoint.target.javaClass.name
        if (excludedPackagePrefixes.any { targetClassName.startsWith(it) }) {
            return joinPoint.proceed()
        }

        val startTime = System.currentTimeMillis()
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val taskTraceId = PulseContextHolder.get()?.traceId ?: UUID.randomUUID().toString()
        val taskName = "${joinPoint.target.javaClass.simpleName}.${joinPoint.signature.name}"

        PulseContextHolder.set(PulseContext(
            traceId = taskTraceId,
            tenantId = "default",
            userId = "system-scheduler",
            source = "scheduled-cron"
        ))

        return try {
            val result = joinPoint.proceed()

            // Capture usage delta
            val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val usage = ResourceUsage(
                cpuUsagePercent = null, // Requires OS MXBean
                memoryUsedBytes = (endMemory - startMemory).coerceAtLeast(0),
                threadCount = Thread.activeCount()
            )

            captor.recordTask(KraftTaskEvent(
                traceId = taskTraceId,
                name = taskName,
                type = KraftTaskType.SCHEDULED,
                status = KraftTaskStatus.SUCCESS,
                durationMs = System.currentTimeMillis() - startTime,
                resourceUsage = usage
            ))
            result
        } catch (ex: Throwable) {
            val duration = System.currentTimeMillis() - startTime

            // Record failure task event
            captor.recordTask(KraftTaskEvent(
                traceId = taskTraceId,
                name = taskName,
                type = KraftTaskType.SCHEDULED,
                status = KraftTaskStatus.FAILURE,
                durationMs = duration,
                errorMessage = ex.message
            ))

            // Map fully to ExceptionEntry
            val exceptionEntry = PulseExceptionEntry(
                traceId = taskTraceId,
                exceptionClass = ex.javaClass.name,
                message = ex.message ?: "Background Cron Failure",
                stackTrace = ex.stackTraceToString(),
                stackSummary = ex.stackTraceToString().lineSequence().take(5).joinToString("\n"),
                path = "scheduled://$taskName",
                method = "RUN",
                statusCode = 500,
                tenantId = "default",
                userId = "system-scheduler",
                requestHeaders = emptyMap(),
                queryParams = emptyMap(),
                hostName = System.getenv("HOSTNAME") ?: "unknown",
                environment = System.getenv("APP_ENV") ?: "prod",
                version = System.getenv("APP_VERSION") ?: "1.0.0",
                isHandled = false,
                metadata = mapOf(
                    "thread_name" to Thread.currentThread().name,
                    "execution_engine" to "SpringAspectProxy"
                )
            )
            captor.recordBackgroundException(exceptionEntry)
            throw ex
        } finally {
            PulseContextHolder.clear()
        }
    }
}