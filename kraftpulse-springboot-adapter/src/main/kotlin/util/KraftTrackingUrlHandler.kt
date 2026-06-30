package util

import interceptor.PulseTelemetryCaptor
import model.KraftHttpClientEvent
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.lang.reflect.Method
import java.net.HttpURLConnection

class KraftTrackingUrlHandler(
    private val delegate: URLStreamHandler,
    private val captor: PulseTelemetryCaptor
) : URLStreamHandler() {

    private val openConnectionMethod: Method = try {
        URLStreamHandler::class.java.getDeclaredMethod("openConnection", URL::class.java).apply {
            isAccessible = true
        }
    } catch (e: Exception) {
        throw RuntimeException("KraftPulse: Failed to access underlying URLStreamHandler openConnection method", e)
    }

    override fun openConnection(url: URL): URLConnection {
        val connection = openConnectionMethod.invoke(delegate, url) as URLConnection
        val startTime = System.currentTimeMillis()
        val currentTraceId = PulseContextHolder.get()?.traceId ?: "outbound-standalone"

        if (url.path.contains("/api/telemetry/ingest")) return connection

        return object : URLConnection(url) {
            override fun connect() = connection.connect()

            override fun getInputStream(): InputStream = try {
                val stream = connection.getInputStream()
                record(true, null)
                stream
            } catch (e: Exception) {
                record(false, e)
                throw e
            }

            private fun record(success: Boolean, ex: Exception?) {
                val httpConn = connection as? HttpURLConnection
                captor.recordOutboundHttp(
                    KraftHttpClientEvent(
                        traceId = currentTraceId,
                        host = url.host ?: "unknown",
                        url = url.toString(),
                        method = httpConn?.requestMethod ?: "GET",
                        statusCode = if (success) (httpConn?.responseCode ?: 200) else 500,
                        durationMs = System.currentTimeMillis() - startTime,
                        responseBodySize = connection.contentLengthLong.coerceAtLeast(0),
                        connectionTimeoutMs = connection.connectTimeout.toLong(),
                        errorMessage = ex?.message
                    )
                )
            }
        }
    }
}