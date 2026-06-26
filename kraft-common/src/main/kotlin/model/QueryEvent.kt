package model

import java.util.UUID

/*
 QueryEvent — the detailed record of a single database operation.
 Populated by the JPA/JDBC interceptor and handed to QueryPulseInterceptor.
*/
data class QueryEvent(
    val id: String = UUID.randomUUID().toString(),
    val traceId: String,

    // The SQL as it was sent to the database (with ? placeholders)
    val sql: String,

    // Bound parameter values in order — kept as strings to avoid serialization issues
    val parameters: List<String?> = emptyList(),

    // Query classification
    val queryType: QueryType,

    // Which JPA entity / table was the primary target (best-effort)
    val entityName: String? = null,
    val tableName: String? = null,

    // Timing — callers fill startedAt, interceptor computes durationMs
    val startedAt: Long,                           // System.currentTimeMillis()
    val durationMs: Long,

    // Outcome
    val rowsAffected: Int = 0,                     // For INSERT/UPDATE/DELETE
    val rowsReturned: Int = 0,                     // For SELECT

    // Performance signals
    val isSlowQuery: Boolean = false,              // True if > configured threshold
    val isPotentialNPlusOne: Boolean = false,

    // Error detail — null when query succeeded
    val error: QueryError? = null,

    // Full JDBC connection metadata (useful for multi-datasource setups)
    val dataSource: String = "primary",
    val databaseProduct: String? = null,           // e.g. "PostgreSQL", "MySQL", "ClickHouse"
    val schema: String? = null,

    // --- NEW FIELDS FOR COMPREHENSIVE OBSERVABILITY ---

    // Multi-tenant footprint
    val tenantId: String? = null,                  // <--- Maps to application isolation contexts

    // Concurrency & Execution Context
    val threadName: String? = null,                // Which application thread executed this
    val isolationLevel: String? = null,            // e.g., "TRANSACTION_READ_COMMITTED"
    val isReadOnly: Boolean = false,               // Indicates if query was routed to a read replica

    // Batch Context
    val isBatch: Boolean = false,                  // Flag if part of executeBatch()
    val batchSize: Int? = null,                    // Size of batch if applicable

    // Tracing & Transaction Deep-Dive
    val transactionId: String? = null,             // ID linking queries in the same DB transaction
    val executionPlan: String? = null              // Optional EXPLAIN output for slow queries
)

enum class QueryType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    CALL,          // Stored procedure
    DDL,           // CREATE / ALTER / DROP (should be rare in prod)
    BATCH,         // executeBatch()
    UNKNOWN
}

data class QueryError(
    val sqlState: String?,
    val errorCode: Int,
    val message: String,
    val exceptionClass: String
)

// ---------------------------------------------------------------------------
// N+1 Detection Support
// Tracks how many times the same query pattern fires in one request context.
// ---------------------------------------------------------------------------

data class QueryPattern(
    val normalizedSql: String,                 // SQL with literals stripped
    val entityName: String?,
    var count: Int = 1
)