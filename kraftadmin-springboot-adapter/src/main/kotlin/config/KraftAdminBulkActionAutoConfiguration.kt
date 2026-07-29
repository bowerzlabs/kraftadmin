package config

import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import persistence.jpa.bulk_actions.BulkActionService
import persistence.jpa.bulk_actions.delete.BulkDeleteService
import persistence.jpa.bulk_actions.export.CsvDataExporter
import persistence.jpa.bulk_actions.export.DataExporter
import persistence.jpa.bulk_actions.export.DataExporterRegistry
import persistence.jpa.bulk_actions.export.JsonDataExporter
import persistence.jpa.bulk_actions.export.XmlDataExporter

/**
 * Wires bulk delete/export/print support for KraftAdmin resource lists.
 *
 * Split out from the main autoconfiguration so the bulk-actions surface
 * (delete service, per-format exporters, registry, façade service) reads
 * as one cohesive unit and can be excluded independently if a consumer
 * wants to fully replace it with their own beans.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftAdminBulkActionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun bulkDeleteService(
        descriptorFactory: KraftAdminDescriptorFactory
    ): BulkDeleteService {
        return BulkDeleteService(descriptorFactory)
    }

    @Bean
    @ConditionalOnMissingBean(JsonDataExporter::class)
    fun jsonDataExporter(
        descriptorFactory: KraftAdminDescriptorFactory
    ): JsonDataExporter {
        return JsonDataExporter(descriptorFactory)
    }

    @Bean
    @ConditionalOnMissingBean(CsvDataExporter::class)
    fun csvDataExporter(
        descriptorFactory: KraftAdminDescriptorFactory
    ): CsvDataExporter {
        return CsvDataExporter(descriptorFactory)
    }

    @Bean
    @ConditionalOnMissingBean(XmlDataExporter::class)
    fun xmlDataExporter(
        descriptorFactory: KraftAdminDescriptorFactory
    ): XmlDataExporter {
        return XmlDataExporter(descriptorFactory)
    }

    /**
     * Collects every DataExporter bean present in the context — the three
     * defaults above, plus any user-supplied custom exporter (e.g. a PDF
     * or Excel exporter) registered as its own @Bean of type DataExporter.
     * Spring resolves the List<DataExporter> param by type, so this works
     * without the registry knowing about individual formats.
     */
    @Bean
    @ConditionalOnMissingBean
    fun dataExporterRegistry(
        exporters: List<DataExporter>
    ): DataExporterRegistry {
        return DataExporterRegistry(exporters)
    }

    @Bean
    @ConditionalOnMissingBean
    fun bulkActionService(
        bulkDeleteService: BulkDeleteService,
        dataExporterRegistry: DataExporterRegistry
    ): BulkActionService {
        return BulkActionService(bulkDeleteService, dataExporterRegistry)
    }

}