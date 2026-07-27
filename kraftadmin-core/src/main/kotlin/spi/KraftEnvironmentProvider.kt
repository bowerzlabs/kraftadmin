package com.kraftadmin.spi

interface KraftEnvironmentProvider {
    fun getAuthMode(): String
    fun getShouldShowLogout(): Boolean
    fun getEnvironmentName(): String
    fun isProduction(): Boolean

    /** All detected data sources — relational, document, key-value, etc. Empty if none detected. */
    fun getDataSources(): List<DataSourceInfo>

    fun getRuntimeInfo(): RuntimeInfo
}

enum class DataSourceKind {
    RELATIONAL, DOCUMENT, KEY_VALUE, SEARCH, UNKNOWN
}

/**
 * Generic across JDBC (Postgres/MySQL/H2), MongoDB, Redis, Elasticsearch,
 * etc. Fields that don't apply to a given kind are simply null rather than
 * the interface having a different shape per database family — keeps the
 * dashboard rendering logic uniform (one card component, driven by `kind`).
 */
data class DataSourceInfo(
    val name: String,              // bean name / logical identifier, useful when multiple sources of the same kind exist (e.g. "primary", "readReplica", "eventsMongo")
    val kind: DataSourceKind,
    val productName: String,       // "PostgreSQL", "MongoDB", "Redis", "Elasticsearch"
    val productVersion: String?,
    val driverOrClientName: String?,
    val connectionString: String,  // credentials-redacted, works for jdbc:// and mongodb:// alike
    val poolType: String?,         // "HikariCP", "Mongo connection pool", null if not pooled
    val activeConnections: Int?,
    val idleConnections: Int?,
    val maxPoolSize: Int?,
    val reachable: Boolean,        // live ping/health-check result — false degrades gracefully rather than showing stale/wrong info
    val extra: Map<String, String> = emptyMap(), // kind-specific extras (e.g. Mongo: replica set name; Redis: eviction policy) without bloating the core shape
)

data class RuntimeInfo(
    val activeProfiles: List<String>,
    val javaVersion: String,
    val startedAt: java.time.Instant,
    val uptimeSeconds: Long,
    val appVersion: String?,
)