package persistence.jpa.bulk_actions

import persistence.jpa.bulk_actions.export.ExportResult

data class BulkDeleteResult(
    val requested: Int,
    val deleted: Int,
    val failed: Map<String, String> // id -> reason
)

sealed class BulkActionOutcome {
    data class Deleted(val result: BulkDeleteResult) : BulkActionOutcome()
    data class Exported(val result: ExportResult) : BulkActionOutcome()
    data class Printed(val result: ExportResult) : BulkActionOutcome()
}