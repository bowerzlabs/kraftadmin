package config

import jakarta.servlet.http.HttpServletRequest
import model.PulseExceptionEntry
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import telemetry.KraftTelemetryService
import util.PulseContextHolder
import java.net.InetAddress
import java.time.Instant

class KraftPulseErrorAttributes(
    private val telemetryService: KraftTelemetryService
) : DefaultErrorAttributes() {

    override fun getErrorAttributes(webRequest: WebRequest, options: ErrorAttributeOptions): Map<String, Any> {
        val attributes = super.getErrorAttributes(webRequest, options)
        val error = getError(webRequest)

        if (error != null) {
            captureException(error, webRequest, attributes)
        }

        return attributes
    }

    private fun captureException(ex: Throwable, request: WebRequest, attrs: Map<String, Any>) {
        val context = PulseContextHolder.get()
        val traceId = (request.getAttribute("traceId", RequestAttributes.SCOPE_REQUEST) as? String)
            ?: context?.traceId
            ?: "err-${System.currentTimeMillis()}"

        val tenantId = context?.tenantId ?: "default"
        val userId = context?.userId

        val path = attrs["path"]?.toString() ?: "unknown"
        val status = (attrs["status"] as? Int) ?: 500

        // Safely extract HTTP method
        val method = (request as? ServletWebRequest)?.request?.method ?: "UNKNOWN"

        // Extract headers
        val headers = request.headerNames.asSequence().associateWith { request.getHeader(it) ?: "" }

        // Construct the Domain Model
        val entry = PulseExceptionEntry(
            id = java.util.UUID.randomUUID().toString(),
            traceId = traceId,
            tenantId = tenantId,
            userId = userId,
            exceptionClass = ex.javaClass.name,
            message = ex.message ?: "No message",
            stackTrace = ex.stackTraceToString(),
            stackSummary = ex.stackTrace.take(5).joinToString("\n") { it.toString() },
            path = path,
            method = method,
            statusCode = status,
            timestamp = Instant.now().toEpochMilli(),
            requestHeaders = headers,
            queryParams = request.parameterMap.mapValues { listOf(it.value.joinToString(",")) },
            hostName = InetAddress.getLocalHost().hostName,
            environment = System.getProperty("kraft.env") ?: "production",
            version = System.getProperty("kraft.version") ?: "1.0.0",
            isHandled = false, // Assuming unhandled if it reached ErrorAttributes
            metadata = mapOf(
                "error_attributes" to attrs.filterKeys { it != "trace" }
            )
        )

        telemetryService.recordException(entry)
    }

}