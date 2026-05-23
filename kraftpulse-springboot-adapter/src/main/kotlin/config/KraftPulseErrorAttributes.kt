package config

import jakarta.servlet.http.HttpServletRequest
import model.PulseExceptionEntry
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.WebRequest
import telementary.KraftTelemetryService
import util.PulseContextHolder

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
        // 1. Pull Contextual IDs from the request attributes (populated by Interceptor)
        // Note: PulseContextHolder might have been cleared if this is called late in the cycle,
        // so we check both the Holder and the Request Attributes for safety.
        val context = PulseContextHolder.get()
        val traceId = (request.getAttribute("traceId", RequestAttributes.SCOPE_REQUEST) as? String)
            ?: context?.traceId
            ?: "err-${System.currentTimeMillis()}"

        val tenantId = context?.tenantId ?: "default"
        val userId = context?.userId

        // 2. Extract Web Metadata
        val path = attrs["path"]?.toString() ?: "unknown"
        val status = (attrs["status"] as? Int) ?: 500

        // 3. Construct the Domain Model
        val entry = PulseExceptionEntry(
            traceId = traceId,
            tenantId = tenantId,
            userId = userId,
            exceptionClass = ex.javaClass.name,
            message = ex.message ?: "No message",
            stackTrace = ex.stackTraceToString(),
            path = path,
            method = ((request as? HttpServletRequest ?: "N/A") as String),
            statusCode = status,
            metadata = mapOf(
                "error_attributes" to attrs.filterKeys { it != "trace" }, // Don't duplicate the trace
                "request_params" to request.parameterMap
            )
        )

        // 4. Record to the 7-day SQLite Sink
        telemetryService.recordException(entry)
    }
}