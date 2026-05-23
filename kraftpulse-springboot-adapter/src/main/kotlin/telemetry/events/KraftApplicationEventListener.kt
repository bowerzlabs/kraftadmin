package telemetry.events

import interceptor.PulseTelemetryCaptor
import model.KraftTaskEvent
import model.KraftTaskStatus
import model.KraftTaskType
import org.springframework.context.ApplicationEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import util.PulseContextHolder
import java.util.UUID

@Component
class KraftApplicationEventListener(
    private val captor: PulseTelemetryCaptor
) {

    @EventListener
    fun handleSpringEvent(event: ApplicationEvent) {
        // Filter out framework noise, capture meaningful lifecycle indicators
        val eventName = event.javaClass.simpleName
        if (eventName.startsWith("Servlet") || eventName.startsWith("Availability")) return

        // Reuse thread context if triggered inside a web request, or spawn an isolated one
        val currentTraceId = PulseContextHolder.get()?.traceId ?: "event-${UUID.randomUUID()}"

        captor.recordTask(
            KraftTaskEvent(
                traceId = currentTraceId,
                name = eventName,
                type = KraftTaskType.APPLICATION_EVENT,
                status = KraftTaskStatus.EMITTED,
                durationMs = 0
            )
        )
    }
}