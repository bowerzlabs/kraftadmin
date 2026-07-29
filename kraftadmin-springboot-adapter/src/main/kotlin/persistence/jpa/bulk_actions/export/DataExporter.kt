package persistence.jpa.bulk_actions.export

import com.kraftadmin.enums.DataFormat

interface DataExporter {
    val format: DataFormat
    fun export(resource: String, selectedIds: List<String>): ExportResult
}