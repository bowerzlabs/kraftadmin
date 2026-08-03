package persistence.jpa.bulk_actions.export

import api.utils.ResourceRow
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kraftadmin.enums.DataFormat
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory

class XmlDataExporter(
    private val descriptorFactory: KraftAdminDescriptorFactory,
) : DataExporter {

    private val mapper = XmlMapper().registerKotlinModule()

    override val format: DataFormat = DataFormat.XML

    override fun export(resource: String, selectedIds: List<String>): ExportResult {
        val rows = descriptorFactory.getResourceDataForExport(resource, selectedIds)
        val suffix = if (selectedIds.isEmpty()) "all" else "selected"

        val items = rows.map { row -> linkedMapOf<String, Any?>("id" to row.id) + row.values }
        val payload = XmlExportPayload(resource = resource, items = items)
        val bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload)

        return ExportResult(
            fileName = "$resource-export-$suffix.${format.fileExtension}",
            contentType = format.contentType,
            content = bytes
        )
    }
}

@JacksonXmlRootElement(localName = "export")
private data class XmlExportPayload(
    @field:JacksonXmlProperty(isAttribute = true)
    val resource: String,

    @field:JacksonXmlElementWrapper(localName = "items")
    @field:JacksonXmlProperty(localName = "item")
    val items: List<Map<String, Any?>>
)