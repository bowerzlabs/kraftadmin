package telemetry.http

import interceptor.PulseTelemetryCaptor
import model.KraftHttpClientEvent
import util.PulseContextHolder
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.lang.reflect.Method

class KraftTrackingUrlHandler(
    private val delegate: URLStreamHandler,
    private val captor: PulseTelemetryCaptor
) : URLStreamHandler() {

    // Safely reflectively grab the protected openConnection method from the base Java class
    private val openConnectionMethod: Method = try {
        URLStreamHandler::class.java.getDeclaredMethod("openConnection", URL::class.java).apply {
            isAccessible = true
        }
    } catch (e: Exception) {
        throw RuntimeException("KraftPulse: Failed to access underlying URLStreamHandler openConnection method", e)
    }

    override fun openConnection(url: URL): URLConnection {
        // Force access to the protected delegate method via reflection invocation
        val connection = openConnectionMethod.invoke(delegate, url) as URLConnection

        val startTime = System.currentTimeMillis()
        val currentTraceId = PulseContextHolder.get()?.traceId ?: "outbound-standalone"

        // Prevent tracing loops when shipping metrics out to your ClickHouse/Cloud sink
        if (url.path.contains("/api/telemetry/ingest")) {
            return connection
        }

        // Return a tracked wrapper that executes metric captures on lifecycle termination/disconnect
        return object : java.net.URLConnection(url) {
            override fun connect() {
                connection.connect()
            }

            override fun getInputStream(): java.io.InputStream = try {
                val stream = connection.getInputStream()
                recordSuccess()
                stream
            } catch (e: Exception) {
                recordFailure(e)
                throw e
            }

            private fun recordSuccess() {
                val duration = System.currentTimeMillis() - startTime
                captor.recordOutboundHttp(
                    KraftHttpClientEvent(
                        traceId = currentTraceId,
                        url = url.toString(),
                        method = "HTTP-CALL", // Protocol agnostic fallback
                        statusCode = 200,
                        durationMs = duration
                    )
                )
            }

            private fun recordFailure(ex: Exception) {
                captor.recordOutboundHttp(
                    KraftHttpClientEvent(
                        traceId = currentTraceId,
                        url = url.toString(),
                        method = "HTTP-CALL",
                        statusCode = 500,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                )
            }
        }
    }
}