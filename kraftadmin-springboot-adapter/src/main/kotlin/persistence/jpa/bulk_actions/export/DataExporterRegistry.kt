package persistence.jpa.bulk_actions.export

import com.kraftadmin.enums.DataFormat
import org.springframework.stereotype.Service
import persistence.jpa.bulk_actions.UnsupportedExportFormatException

@Service
class DataExporterRegistry(
    exporters: List<DataExporter>,
) {
    private val byFormat: Map<DataFormat, DataExporter> = exporters.associateBy { it.format }

    fun getExporter(format: DataFormat): DataExporter =
        byFormat[format] ?: throw UnsupportedExportFormatException(format.name)
}