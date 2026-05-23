package model

data class PulseContext(
    /** Unique ID for the current request/trace (e.g., X-Request-ID). */
    val traceId: String = "standalone",

    /** The organization or client owning this data (Crucial for B2B BI). */
    val tenantId: String? = "default",

    /** The ID of the user who initiated the action. */
    val userId: String? = null,

    /** Where the call originated (e.g., "Enthuzd-Fleet-Service", "KraftAdmin-UI"). */
    val source: String = "jpa-sniffer",

    /** Optional: The specific feature or module (e.g., "Reports", "Billing"). */
    val tags: Map<String, String> = emptyMap()
){
    companion object {
        val SYSTEM_INTERNAL = PulseContext()
        val SYSTEM_DEFAULT = PulseContext(
            traceId = "internal-pulse",
            source = "jpa-standalone-sniffer"
        )
    }


}