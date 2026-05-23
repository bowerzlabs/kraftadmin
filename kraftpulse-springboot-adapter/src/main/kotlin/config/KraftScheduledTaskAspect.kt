package config

import interceptor.PulseTelemetryCaptor
import model.KraftTaskEvent
import model.KraftTaskStatus
import model.KraftTaskType
import model.PulseExceptionEntry
import org.springframework.stereotype.Component
import util.PulseContextHolder
import model.PulseContext
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import java.util.UUID

@Aspect
@Component
class KraftScheduledTaskAspect(
    private val captor: PulseTelemetryCaptor
) {

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    fun profileScheduledTask(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = System.currentTimeMillis()
        val taskTraceId = "task-${UUID.randomUUID()}"
        val taskName = "${joinPoint.target.javaClass.simpleName}.${joinPoint.signature.name}"

        // Set context safely on the actual worker thread executing the cron
        PulseContextHolder.set(
            PulseContext(
                traceId = taskTraceId,
                tenantId = "default",
                userId = "system-scheduler",
                source = "scheduled-cron"
            )
        )

        // Log START phase
        captor.recordTask(
            KraftTaskEvent(
                traceId = taskTraceId,
                name = taskName,
                type = KraftTaskType.SCHEDULED,
                status = KraftTaskStatus.START
            )
        )

        return try {
            val result = joinPoint.proceed()

            // Log SUCCESS phase
            captor.recordTask(
                KraftTaskEvent(
                    traceId = taskTraceId,
                    name = taskName,
                    type = KraftTaskType.SCHEDULED,
                    status = KraftTaskStatus.SUCCESS,
                    durationMs = System.currentTimeMillis() - startTime
                )
            )
            result
        } catch (ex: Throwable) {
            val duration = System.currentTimeMillis() - startTime

            // Log FAILURE phase
            captor.recordTask(
                KraftTaskEvent(
                    traceId = taskTraceId,
                    name = taskName,
                    type = KraftTaskType.SCHEDULED,
                    status = KraftTaskStatus.FAILURE,
                    durationMs = duration,
                    errorMessage = ex.message
                )
            )

            // Capture background exception record
            val exceptionEntry = PulseExceptionEntry(
                id = UUID.randomUUID().toString(),
                traceId = taskTraceId,
                exceptionClass = ex.javaClass.name,
                message = ex.message ?: "Background Cron Failure",
                stackTrace = ex.stackTraceToString(),
                path = "scheduled://$taskName",
                method = "RUN",
                statusCode = 500,
                timestamp = System.currentTimeMillis(),
                tenantId = "default",
                userId = "system-scheduler",
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