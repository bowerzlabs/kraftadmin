package telemetry.telemetry

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class InitSqliteDB : BaseJavaMigration() {

    override fun migrate(context: Context) {
        context.connection.createStatement().use { statement ->
            // 1. Create Tables
            statement.execute("""
                CREATE TABLE kraft_http_client_events (
                    id TEXT PRIMARY KEY,
                    trace_id TEXT NOT NULL,
                    host TEXT,
                    url TEXT,
                    method TEXT,
                    status_code INTEGER,
                    duration_ms INTEGER,
                    response_body_size INTEGER DEFAULT 0,
                    connection_timeout_ms INTEGER,
                    error_message TEXT,
                    created_at INTEGER,
                    payload TEXT,
                    synced INTEGER DEFAULT 0
                );

                CREATE TABLE kraft_tasks (
                    id TEXT PRIMARY KEY,
                    trace_id TEXT NOT NULL,
                    name TEXT,
                    type TEXT,
                    status TEXT,
                    duration_ms INTEGER,
                    error_message TEXT,
                    resource_usage TEXT,
                    node_identifier TEXT,
                    retry_count INTEGER DEFAULT 0,
                    trigger_source TEXT,
                    task_metadata TEXT,
                    created_at INTEGER,
                    payload TEXT,
                    synced INTEGER DEFAULT 0
                );

                CREATE TABLE kraft_telemetry (
                    id TEXT PRIMARY KEY,
                    trace_id TEXT,
                    type TEXT,
                    resource TEXT,
                    action TEXT,
                    duration_ms INTEGER,
                    status INTEGER,
                    actor TEXT,
                    ip_address TEXT,
                    user_agent TEXT,
                    device_type TEXT,
                    referer TEXT,
                    geolocation TEXT,
                    impact TEXT,
                    request_details TEXT,
                    created_at INTEGER,
                    payload TEXT,
                    synced INTEGER DEFAULT 0
                );

                CREATE TABLE kraft_exceptions (
                    id TEXT PRIMARY KEY,
                    trace_id TEXT NOT NULL,
                    tenant_id TEXT,
                    user_id TEXT,
                    exception_class TEXT,
                    message TEXT,
                    stack_trace TEXT,
                    stack_summary TEXT,
                    path TEXT,
                    method TEXT,
                    status_code INTEGER,
                    request_headers TEXT,
                    query_params TEXT,
                    host_name TEXT,
                    environment TEXT,
                    version TEXT,
                    is_handled INTEGER DEFAULT 0,
                    metadata TEXT,
                    created_at INTEGER,
                    payload TEXT,
                    synced INTEGER DEFAULT 0
                );

                CREATE TABLE kraft_query_events (
                    id TEXT PRIMARY KEY,
                    trace_id TEXT NOT NULL,
                    sql TEXT,
                    parameters TEXT,
                    query_type TEXT,
                    entity_name TEXT,
                    table_name TEXT,
                    started_at INTEGER,
                    duration_ms INTEGER,
                    rows_affected INTEGER,
                    rows_returned INTEGER,
                    is_slow INTEGER DEFAULT 0,
                    is_n_plus_one INTEGER DEFAULT 0,
                    data_source TEXT,
                    database_product TEXT,
                    schema TEXT,
                    tenant_id TEXT,
                    thread_name TEXT,
                    isolation_level TEXT,
                    is_read_only INTEGER DEFAULT 0,
                    is_batch INTEGER DEFAULT 0,
                    batch_size INTEGER,
                    transaction_id TEXT,
                    execution_plan TEXT,
                    error_details TEXT,
                    created_at INTEGER,
                    payload TEXT,
                    synced INTEGER DEFAULT 0
                );
            """.trimIndent())

            // 2. Create Indexes
            statement.execute("""
                CREATE INDEX idx_http_client_trace ON kraft_http_client_events(trace_id);
                CREATE INDEX idx_http_client_created ON kraft_http_client_events(created_at, synced);
                CREATE INDEX idx_telemetry_trace ON kraft_telemetry(trace_id);
                CREATE INDEX idx_telemetry_sync ON kraft_telemetry(synced, created_at);
                CREATE INDEX idx_query_trace ON kraft_query_events(trace_id);
                CREATE INDEX idx_query_sync ON kraft_query_events(synced, created_at);
                CREATE INDEX idx_exc_trace ON kraft_exceptions(trace_id);
                CREATE INDEX idx_task_trace ON kraft_tasks(trace_id);
            """.trimIndent())
        }
    }
}