package com.kraftadmin.controller

import com.kraftadmin.util.KraftSpringLoggingService
import com.kraftadmin.utils.logging.KraftLogEntry
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/api/system/logs")
class KraftAdminSpringbootLogController(private val logService: KraftSpringLoggingService) {

    @GetMapping
    fun getLogs(): List<KraftLogEntry> {
        // Returns the latest 500 logs from memory
        return logService.getAll()
    }

//    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
//    fun streamLogs(): Flux<KraftLogEntry> {
//        // Requires Project Reactor (Spring WebFlux)
//        // or a simple SseEmitter implementation
//        return Flux.interval(Duration.ofMillis(500))
//            .map { logService.getAll().first() }
//            .distinctUntilChanged()
//    }


}