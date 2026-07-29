package persistence.jpa.bulk_actions.export

import com.kraftadmin.enums.DataFormat
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory

class CsvDataExporter(
    private val descriptorFactory: KraftAdminDescriptorFactory,
) : DataExporter {

    override val format: DataFormat = DataFormat.CSV

    override fun export(resource: String, selectedIds: List<String>): ExportResult {
        val rows = descriptorFactory.getLookupDataForExport(resource, selectedIds)
        val suffix = if (selectedIds.isEmpty()) "all" else "selected"
        val builder = StringBuilder()

        builder.append("id,label").append(CRLF)
        rows.forEach { row ->
            builder.append(escapeCsvField(row.id))
                .append(',')
                .append(escapeCsvField(row.label))
                .append(CRLF)
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
     * Without this, a label like `Smith, "Big Deal", Inc.` would silently
     * corrupt the column count for every row after it.
     */
    private fun escapeCsvField(value: Any?): String {
        val text = value?.toString() ?: ""
        val needsQuoting = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private companion object {
        // CRLF per RFC 4180; Excel in particular is picky about \n-only line endings.
        const val CRLF = "\r\n"
    }
}