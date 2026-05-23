package interceptor

import model.KraftHttpClientEvent
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import util.PulseContextHolder
import java.io.IOException

class KraftHttpClientInterceptor(
    private val captor: PulseTelemetryCaptor
) : ClientHttpRequestInterceptor {

    @Throws(IOException::class)
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val startTime = System.currentTimeMillis()

        // Retain context alignment: pull active trace context, or tag as an isolated background call
        val currentTraceId = PulseContextHolder.get()?.traceId ?: "outbound-standalone"

        var statusCode = 0
        try {
            val response = execution.execute(request, body)
            statusCode = response.statusCode.value()
            return response
        } catch (ex: Throwable) {
            statusCode = 500 // Map transport layer timeouts/connection failures as internal errors
            throw ex
        } finally {
            val duration = System.currentTimeMillis() - startTime

            // Prevent recursive tracking loops if the system is flushing telemetry batches to the Cloud URL endpoint
            if (!request.uri.path.contains("/api/telemetry/ingest")) {
                captor.recordOutboundHttp(
                    KraftHttpClientEvent(
                        traceId = currentTraceId,
                        url = request.uri.toString(),
                        method = request.method.name(),
                        statusCode = statusCode,
                        durationMs = duration
                    )
                )
            }
        }
    }
}