package com.kraftadmin.utils.telementary

/**
 * Interface for components that send telemetry to external cloud systems.
 */
interface KraftTelemetryEmitter {
    /**
     * Publishes the event to the cloud provider.
     */
    fun emit(event: KraftTelemetryEvent)

    /**
     * The unique name of the emitter (e.g., "DatadogEmitter", "BigQueryEmitter").
     */
    val name: String
}