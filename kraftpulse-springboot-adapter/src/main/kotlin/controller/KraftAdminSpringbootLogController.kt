package controller

import analytics.TelemetryWithQueries
import security.AdminUserDTO
import logging.KraftLogAction
import logging.KraftLogLevel
import util.KraftSpringLoggingService
import logging.KraftLogEntry
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import telemetry.KraftTelemetryEvent
import java.util.concurrent.CopyOnWriteArrayList

@RestController
@RequestMapping("/admin/api/system/logs")
//@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true")
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
class KraftAdminSpringbootLogController(
    private val logService: KraftSpringLoggingService
) {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    @GetMapping
    fun getLogs(): List<TelemetryWithQueries> {
//        val logs = logService.getAll()
        val logs = logService.getAll()
//        println("logs $logs")
       return logs
    }

    // FIX: Listen for the LogEntry published by KraftSpringLoggingService
    @EventListener
    fun handleLogEntry(entry: KraftLogEntry) {
        broadcast(entry)
    }

    // This handles the SQLite -> Telemetry event path
    @EventListener
    fun handlePersistedEvent(event: KraftTelemetryEvent) {
        val logEntry = KraftLogEntry(
            level = KraftLogLevel.AUDIT,
            message = "${event.action} ${event.resource}",
            timestamp = event.timestamp,
            action = KraftLogAction.CREATE,
            resource = event.resource,
            resourceId = event.id,
            actor = AdminUserDTO("Test", "test@gmail.com", emptySet(), "TU", "", false),
//            trace = event.ipAddress,
        )
        // We push this to the service buffer so it shows up in history (getLogs)
        // Note: logService.push will publish another event, so handleLogEntry will broadcast it.
//        logService.push(event)
    }

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamLogs(): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        // Send an empty "ping" to keep the connection alive immediately
        emitter.send(SseEmitter.event().comment("connection established"))
        emitters.add(emitter)

        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }

        return emitter
    }

    private fun broadcast(entry: KraftLogEntry) {
        val deadEmitters = mutableListOf<SseEmitter>()
        emitters.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().data(entry))
            } catch (e: Exception) {
                deadEmitters.add(emitter)
            }
        }
        emitters.removeAll(deadEmitters)
    }
}