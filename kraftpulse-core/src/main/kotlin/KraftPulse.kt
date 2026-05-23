package telemetry

import model.PulseContext
import model.PulseExceptionEntry
import telementary.KraftTelemetryService
import java.util.UUID

object KraftPulse {
    private var started = false
    private lateinit var sink: KraftTelemetryService

    // Linked to PulseContextHolder or used independently for thread-safety
    private val contextThreadLocal = ThreadLocal<PulseContext>()

    fun init(telemetrySink: KraftTelemetryService) {
        this.sink = telemetrySink

        // Only print the banner if we aren't using a No-Op sink
        if (telemetrySink.javaClass.simpleName != "NoOpTelemetryService") {
            start()
        }
//        start()
    }

    fun start() {
        if (started) return
        println("=".repeat(40))
        println("KRAFT PULSE: Engine ALIVE")
        println("=".repeat(40))
        started = true
    }

    /**
     * Set by Interceptor or Manual entry to define the current environment context.
     */
    fun enter(context: PulseContext) = contextThreadLocal.set(context)

    fun getContext(): PulseContext? = contextThreadLocal.get()

    fun exit() = contextThreadLocal.remove()

    /**
     * Captures an exception and maps it to the 7-day monitoring sink.
     */
    fun recordException(ex: Throwable, metadata: Map<String, Any?> = emptyMap()) {
        if (!started) return

        val currentContext = contextThreadLocal.get()

        // Extract status code from metadata if provided (e.g. by a Web Interceptor),
        // otherwise default to 500 for internal/database errors.
        val statusCode = (metadata["status_code"] as? Int) ?: 500

        val entry = PulseExceptionEntry(
            id = UUID.randomUUID().toString(),
            traceId = currentContext?.traceId ?: "standalone-${System.currentTimeMillis()}",
            tenantId = currentContext?.tenantId,
            userId = currentContext?.userId,
            exceptionClass = ex.javaClass.name,
            message = ex.message ?: "No message",
            stackTrace = ex.stackTraceToString(),
            path = metadata["path"] as? String ?: "internal",
            method = metadata["method"] as? String ?: "N/A",
            statusCode = statusCode,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )

        sink.recordException(entry)
    }

    fun stop() {
        started = false
    }
}