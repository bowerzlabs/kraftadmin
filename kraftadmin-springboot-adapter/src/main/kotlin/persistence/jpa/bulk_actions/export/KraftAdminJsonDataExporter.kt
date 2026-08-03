package persistence.jpa.bulk_actions.export

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kraftadmin.enums.DataFormat
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import org.springframework.stereotype.Component

@Component
class JsonDataExporter(
    private val descriptorFactory: KraftAdminDescriptorFactory,
) : DataExporter {

    private val mapper = ObjectMapper().registerKotlinModule()

    override val format: DataFormat = DataFormat.JSON

    override fun export(resource: String, selectedIds: List<String>): ExportResult {
        val rows = descriptorFactory.getResourceDataForExport(resource, selectedIds)
        val suffix = if (selectedIds.isEmpty()) "all" else "selected"

        // Flatten id + values into one row per record — the actual stored
        val payload = rows.map { row -> linkedMapOf<String, Any?>("id" to row.id) + row.values }

        val bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload)
        return ExportResult(
            fileName = "$resource-export-$suffix.${format.fileExtension}",
            contentType = format.contentType,
            content = bytes
        )
    }

}