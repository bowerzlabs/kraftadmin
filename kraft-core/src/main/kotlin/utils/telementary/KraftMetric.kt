package com.kraftadmin.utils.telementary

import java.time.Instant


/**
 * Captures numeric metrics for Business Intelligence dashboards.
 */
data class KraftMetric(
    val name: String,               // e.g., "admin.resource.edit.count"
    val value: Double,
    val tags: Map<String, String>,  // e.g., "resource" to "Talent"
    val timestamp: Instant = Instant.now()
)


class KraftTelemetryEngine(private val emitters: List<KraftTelemetryEmitter>) {

//    fun trackAction(
//        actor: AdminActor,
//        action: String,
//        resource: String,
//        changes: List<FieldChange>? = null,
//        context: Map<String, Any?> = emptyMap()
//    ) {
//        val event = KraftTelemetryEvent(
//            actor = actor,
//            action = action,
//            resource = resource,
//            changes = changes,
//            metadata = context
//        )
//
//        // Broadcast to all cloud providers (Async to prevent UI lag)
//        emitters.forEach { it.emit(event) }
//    }
}