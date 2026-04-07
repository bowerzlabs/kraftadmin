package com.kraftadmin.utils.telementary

interface KraftTelemetryService {
    /**
     * Records a telemetry event. Implementation should be non-blocking.
     */
    fun record(event: KraftTelemetryEvent)

    /**
     * Fetches the latest 'pulse' data for the UI.
     * This allows the Svelte dashboard to show real-time charts.
     */
    fun getPulse(limit: Int = 100): List<KraftTelemetryEvent>

    /**
     * Optional: Clear telemetry data.
     */
    fun purge()

    fun flushToCloud()
}