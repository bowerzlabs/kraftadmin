package persistence.jpa.bulk_actions

import com.kraftadmin.enums.DataFormat
import com.kraftadmin.logging.KraftAdminLogging
import persistence.jpa.bulk_actions.delete.BulkDeleteService
import persistence.jpa.bulk_actions.export.DataExporterRegistry
import persistence.jpa.bulk_actions.export.ExportResult

class BulkActionService(
    private val bulkDeleteService: BulkDeleteService,
    private val dataExporterRegistry: DataExporterRegistry,
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    // Delete always requires explicit ids — never delete the whole table
    // implicitly just because the id list was omitted.
    fun deleteBulk(resource: String, selectedIds: List<String>): BulkDeleteResult {
        if (selectedIds.isEmpty()) throw EmptySelectionException("DELETE")

        val result = bulkDeleteService.bulkDelete(resource, selectedIds)
        logger.info(
            "Bulk delete on {}: requested={}, deleted={}, failed={}",
            resource, result.requested, result.deleted, result.failed.size
        )
        return result
    }

    // Empty selectedIds means "export the entire resource" — this is
    // intentional, not a missing-parameter case.
    fun exportBulk(resource: String, selectedIds: List<String>, format: String): ExportResult {
        val dataFormat = DataFormat.fromStringOrNull(format)
            ?: throw UnsupportedExportFormatException(format)
        val exporter = dataExporterRegistry.getExporter(dataFormat)
        return exporter.export(resource, selectedIds)
    }

    fun printBulk(resource: String, selectedIds: List<String>): ExportResult {
        if (selectedIds.isEmpty()) throw EmptySelectionException("PRINT")
        throw NotImplementedError("Bulk print is not yet implemented for resource '$resource'.")
    }
}