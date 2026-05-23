package com.kraftadmin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import telemetry.KraftPulse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@RestController
class AppController(
    private val backgroundService: KraftPulseTestBackgroundService,
    private val restTemplate: RestTemplate // Managed instance intercepted by BeanPostProcessor
) {

    @GetMapping("/test-error")
    fun triggerError(@RequestParam(required = false) message: String?): String {
        throw RuntimeException(message ?: "KraftPulse Test Exception: Something went wrong!")
    }

    @GetMapping("/test-manual-capture")
    fun manualCapture(): String {
        return try {
            val list = listOf("A", "B")
            println(list[5])
            "Success"
        } catch (e: Exception) {
            KraftPulse.recordException(e, mapOf(
                "custom_key" to "Testing Manual Injection",
                "layer" to "Controller"
            ))
            "Captured"
        }
    }

    @GetMapping("/test-event")
    fun triggerEvent(@RequestParam(defaultValue = "Hello from BowerzLabs!") msg: String): String {
        backgroundService.publishTestEvent(msg)
        return "Event Dispatched"
    }

    // 1. TEST RESTTEMPLATE: Automatically Instrumentated via Interceptor
    @GetMapping("/test-outbound-rest")
    fun testOutboundRest(): String? {
        val url = "https://jsonplaceholder.typicode.com/posts/1"
        // This will fire KraftHttpClientInterceptor automatically
        return restTemplate.getForObject(url, String::class.java)
    }

    // 2. TEST JDK HTTPCLIENT: Explicit manual tracing wrap if not globally instrumented
    @GetMapping("/test-outbound-jdk")
    fun testOutboundJdk(): String {
        val url = "https://jsonplaceholder.typicode.com/todos/1"
        val client = HttpClient.newHttpClient()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        // Note: For native JDK HttpClient or WebClient, if you want it tracked automatically,
        // it requires its own customized exchange filter/interceptor configuration.
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        println("response: $response")
        return "JDK Client Response Status: ${response.statusCode()}"
    }
}
