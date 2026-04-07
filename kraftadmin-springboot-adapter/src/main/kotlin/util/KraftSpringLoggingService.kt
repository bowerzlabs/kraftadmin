package com.kraftadmin.util

import com.kraftadmin.utils.logging.KraftLogEntry
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class KraftSpringLoggingService {
    private val buffer = ConcurrentLinkedQueue<KraftLogEntry>()
    private val maxLimit = 500

    fun push(entry: KraftLogEntry) {
        if (buffer.size >= maxLimit) buffer.poll()
        buffer.add(entry)
    }

    fun getAll(): List<KraftLogEntry> = buffer.toList().reversed()
}