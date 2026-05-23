package analytics

data class AnalyticsSummary(
    val totalRequests: Long = 0,
    val avgLatency: Double = 0.0,
    val errorRate: Double = 0.0,
    val uniqueActors: Long = 0,
    val uniqueIps: Long = 0
)