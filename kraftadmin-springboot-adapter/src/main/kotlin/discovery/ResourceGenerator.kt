package discovery

import com.kraftadmin.model.BulkDeleteOutcome
import com.kraftadmin.spi.AbstractResource
import com.kraftadmin.spi.DiscoveredEntity
import com.kraftadmin.spi.KraftAdminResource
import config.KraftAdminProperties
import discovery.descriptors.action.ActionDescriptorBuilder
import discovery.descriptors.column.ColumnBuildStrategyFactory
import discovery.descriptors.column.KraftColumnBuilder
import events.SpringActionRegistry
import org.springframework.context.ApplicationContext
import persistence.EntityMetadataFactory
import persistence.KraftDataProviderFactory

object ResourceGenerator {

    fun <T : Any> generate(
        discoveredEntity: DiscoveredEntity<T>,
        context: ApplicationContext,
        properties: KraftAdminProperties,
    ): KraftAdminResource<T> {

        val kClass = discoveredEntity.entityClass
        val metadata = EntityMetadataFactory.create(
            discoveredEntity.provider,
            discoveredEntity.entityClass.kotlin
        )

        val actionRegistry = context.getBean(SpringActionRegistry::class.java)

        val adminResource =
            discoveredEntity.entityClass.getAnnotation(com.kraftadmin.annotations.KraftAdminResource::class.java)

        val columnBuilder = KraftColumnBuilder(
            ColumnBuildStrategyFactory.create(discoveredEntity.provider)
        )

        val actionBuilder = ActionDescriptorBuilder(actionRegistry)

        val resource = object : AbstractResource<T>(
            name = kClass.simpleName ?: "Unknown",
            label = adminResource?.label?.ifBlank { kClass.simpleName ?: "Unknown" }
                ?: kClass.simpleName ?: "Unknown",
            entityClass = discoveredEntity.entityClass.kotlin,
            group = adminResource?.group ?: "Main",
            icon = adminResource?.icon ?: "📁",
            isHidden = adminResource?.hidden ?: false,
            isSearchable = adminResource?.searchable ?: true,
            defaultSort = adminResource?.defaultSort ?: "",
            isReadOnly = adminResource?.readOnly ?: false,
            pageSize = adminResource?.pageSize ?: 20,
            permissionScope = adminResource?.permissionScope ?: "ALL",
            isExportable = adminResource?.exportable ?: true,
            provider = discoveredEntity.provider,
        ) {

            init {
                val builtColumns = columnBuilder.build(
                    entityClass = discoveredEntity.entityClass.kotlin
                )
                this.columns = builtColumns
            }

            override val customActions by lazy {
                if (adminResource == null) {
                    emptyList()
                } else {
                    actionBuilder.build(adminResource::class)
                }
            }

            override val searchableColumns by lazy {
                metadata.searchableFields
            }

            override val sortableColumns by lazy {
                metadata.sortableFields
            }

            override fun getIdentifier(entity: T): Any =
                metadata.getIdentifier(entity)
                    ?: error(
                        "Entity identifier cannot be null: " +
                                entityClass.qualifiedName
                    )

            override fun bulkDelete(ids: List<String>): BulkDeleteOutcome {
                return dataProvider?.bulkDelete(ids)
                    ?: com.kraftadmin.model.BulkDeleteOutcome(
                        requested = ids.size,
                        deleted = 0,
                        failed = ids.associateWith { "No data provider configured for this resource." }
                    )
            }

        }

        attachDataProvider(resource, discoveredEntity, context, properties)

        return resource
    }

    private fun <T : Any> attachDataProvider(
        resource: AbstractResource<T>,
        discoveredEntity: DiscoveredEntity<T>,
        context: ApplicationContext,
        properties: KraftAdminProperties
    ) {
        @Suppress("UNCHECKED_CAST")
        val factories = context.getBeansOfType(KraftDataProviderFactory::class.java)
            .values as Collection<KraftDataProviderFactory<T>>

        val provider = factories
            .firstOrNull { it.supports(discoveredEntity.provider) }
            ?.create(discoveredEntity, context, properties)

        if (provider == null) {
            // log.warn("No data provider available for ${discoveredEntity.entityClass.simpleName} (${discoveredEntity.provider})")
            return
        }

        resource.dataProvider = provider
    }
}