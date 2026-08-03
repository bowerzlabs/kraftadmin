package persistence.jpa.bulk_actions.export

import com.kraftadmin.enums.DataFormat
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory

class CsvDataExporter(
    private val descriptorFactory: KraftAdminDescriptorFactory,
) : DataExporter {

    override val format: DataFormat = DataFormat.CSV

    override fun export(resource: String, selectedIds: List<String>): ExportResult {
        val rows = descriptorFactory.getResourceDataForExport(resource, selectedIds)
        val suffix = if (selectedIds.isEmpty()) "all" else "selected"

        // Column set is derived from the actual data — union of every row's
        val columns = linkedSetOf("id")
        rows.forEach { row -> columns.addAll(row.values.keys) }

        val builder = StringBuilder()
        builder.append(columns.joinToString(",") { escapeCsvField(it) }).append(CRLF)

        rows.forEach { row ->
            val rowValues = linkedMapOf<String, Any?>("id" to row.id)
            rowValues.putAll(row.values)

            builder.append(
                columns.joinToString(",") { column -> escapeCsvField(rowValues[column]) }
            ).append(CRLF)
        }

        return ExportResult(
            fileName = "$resource-export-$suffix.${format.fileExtension}",
            contentType = format.contentType,
            content = builder.toString().toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * RFC 4180 quoting: wrap the field in double quotes if it contains a
     * comma, double quote, or line break, and double up any embedded quotes.
     * Also flattens non-scalar values (nested maps/relations/embedded
     * objects) to a JSON-ish string rather than dumping Kotlin's default
     * toString() representation.
     */
    private fun escapeCsvField(value: Any?): String {
        val text = when (value) {
            null -> ""
            is String, is Number, is Boolean -> value.toString()
            else -> value.toString()
        }
        val needsQuoting = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private companion object {
        const val CRLF = "\r\n"
    }
}