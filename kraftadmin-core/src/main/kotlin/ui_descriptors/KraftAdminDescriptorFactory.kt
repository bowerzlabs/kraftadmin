package com.kraftadmin.ui_descriptors


import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.BuildInfo
import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.api.responses.ResourceDataResponse
import com.kraftadmin.config.KraftAdminPropertiesConfig
import com.kraftadmin.config.KraftAdminRuntimeConfig
import com.kraftadmin.enums.ProviderType
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.model.BulkDeleteOutcome
import com.kraftadmin.spi.EntityDiscoveryService
import com.kraftadmin.spi.KraftDataProvider
import security.SecurityProviderChain
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.utils.validation.KraftValidationExtractor
import com.kraftadmin.utils.validation.ValidationResponse
import kotlin.collections.find
import kotlin.collections.map
import kotlin.reflect.KClass

class KraftAdminDescriptorFactory(
    private val runtimeConfig: KraftAdminRuntimeConfig,
    private val validationExtractor: KraftValidationExtractor,
    private val environmentProvider: KraftEnvironmentProvider,
    private val entityDiscoverer: EntityDiscoveryService
) {

    private val logger = KraftAdminLogging.logger(javaClass)


    fun create(
        chain: SecurityProviderChain,
        pConfig: KraftAdminPropertiesConfig,
    ): KraftAdminDescriptor {
        val config = runtimeConfig.config

        // Use the chain to find the user across ALL providers
        val currentUser = chain.resolveCurrentUser()

        val env = EnvironmentDescriptor(
            name = environmentProvider.getEnvironmentName(),
            authMode = environmentProvider.getAuthMode(),
            version = BuildInfo.VERSION,
            showLogout = environmentProvider.getShouldShowLogout(),
            theme = ThemeDescriptor(
                primaryColor = pConfig.theme.primaryColor,
                darkMode = pConfig.theme.darkMode,
                logoUrl = pConfig.logoUrl
            ),
            features = pConfig.features,
            pagination = pConfig.pagination,
            locale = pConfig.localeConfig
        )

        return KraftAdminDescriptor(
            basePath = pConfig.basePath,
            title = pConfig.title,
            version = BuildInfo.VERSION,
            environment = env,
            currentUser = currentUser,
            resources = config.generatedResources.map { it.toDescriptor() }
        )
    }

    // Fetch One
    private fun findResource(name: String) = runtimeConfig.resourcesByName.values.find {
        it.name.equals(name, ignoreCase = true)
    } ?: throw IllegalArgumentException("Resource '$name' not found.")

    // Fetch List
    fun getResourceData(
        name: String,
        page: Int = 1,
        size: Int = 20,
        query: String?,
        sortField: String?,
        sortDirection: String?,
    ): ResourceDataResponse {
        val resource = findResource(name)
        val columns = resource.columns // columns comes from the SPI resource

        // Fetch the data using the pagination logic we built
        val pagedData = resource.getAllRows(page, size, query, columns, sortField, sortDirection)

        //  Map the resource to its Descriptor and attach the live data
        val descriptor = ResourceDescriptor(
            name = resource.name,
            label = resource.label,
            customActions = resource.customActions,
            columns = columns.map { it.toDescriptor() },
            data = pagedData,
            group = resource.group,
            icon = resource.icon,
            hidden = resource.isHidden,
            searchable = resource.isSearchable,
            defaultSort = resource.defaultSort,
            readOnly = resource.isReadOnly,
            pageSize = resource.pageSize,
            permissionScope = resource.permissionScope,
            exportable = resource.isExportable,
            totalCount = getTotalCountForResource(resource.name),
            searchableFields = resource.searchableColumns,
            sortableFields = resource.sortableColumns,
            provider = resource.provider
        )

        return ResourceDataResponse(resource = descriptor)
    }

    /**
     * The primary entry point for saving data with a "Pre-flight" validation check.
     */
    fun validateAndSave(provider: String, payload: Map<String, Any?>): ValidationResponse {
        val resource = findResource(provider)
        val formData = (payload["data"] as? Map<*, *>) ?: payload
        val errors = mutableMapOf<String, List<String>>()

        resource.columns.forEach { col ->
            val fieldErrors = validationExtractor.validate(col, formData[col.name])
            logger.info("${col.name} errors: $fieldErrors")
            if (fieldErrors.isNotEmpty()) {
                errors[col.name] = fieldErrors
            }
        }

        // If this block isn't here, it will ALWAYS save
        if (errors.isNotEmpty()) {
//            logger.warn("Save blocked by validation errors: $errors")
            return ValidationResponse(success = false, errors = errors)
        }

        // Only save if errors is empty
        val savedData = resource.save(provider, payload)
        return ValidationResponse(success = true, data = savedData)
    }

    fun getResourceDetailsData(name: String, id: String): ResourceRow? {
         val resource = findResource(name)
        return resource.getById(id)
    }

    // Delete
    fun deleteResource(name: String, id: String) : KraftOperationResponse<Unit>? {
        val resource = findResource(name)
        return resource.delete(id)
    }

    fun bulkDeleteResource(name: String, ids: List<String>): BulkDeleteOutcome {
        val resource = findResource(name)
        return resource.bulkDelete(ids)
    }


    fun getLookupData(resourceName: String, search: String): List<ObjectResponse> {
        val resource = findResource(resourceName)
        val provider = resource.dataProvider
            ?: throw IllegalStateException("Resource '$resourceName' does not use any KraftDataProvider.")
        val lookup = LookupDescriptor(
            targetEntity = resourceName,
            searchableFields = resource.searchableColumns,
        )
        return provider.getLookupData(lookup, 20, search)
    }

    fun getLookupDataByIds(name: String,  ids: List<String>): List<ObjectResponse> {
        val resource = findResource(name)
        val provider = resource.dataProvider
            ?: throw IllegalStateException("Resource '$name' does not use any KraftDataProvider.")
        val lookup = LookupDescriptor(
            targetEntity = name,
        )
        return provider.getLookupDataByIds(lookup, ids)
    }

    // Build the lookup map once (or on demand)
    private val resourceRegistry: Map<String, KClass<*>> by lazy {
        entityDiscoverer.discover(
            provider = ProviderType.JPA
        )
            .map { it.entityClass.kotlin }
            .associateBy { it.simpleName ?: "" }
    }

    /**
     * Maps the string provider (e.g., "Venue") back to the annotated Kotlin Class.
     */
    fun getEntityClassForResource(resource: String): KClass<*>? {
        return resourceRegistry[resource]
    }

    /**
     * Returns all resource names (e.g., ["Venue", "Event", "User"])
     */
    fun getRegisteredResourceNames(): List<String> {
        return runtimeConfig.resourcesByName.keys.toList()
    }

    /**
     * Gets the live count of rows for a specific resource.
     */
    fun getTotalCountForResource(name: String): Long {
        val resource = findResource(name)
        return resource.countAll(name) ?: 0
    }

    fun getDataProviderForResource(resourceName: String): KraftDataProvider<out Any>? {
        val resource = findResource(resourceName)
        val provider = resource.dataProvider
        return provider
    }

    /**
     * Returns the underlying SPI resource if needed for metadata checks.
     */
//    fun getResourceData(provider: String): ResourceDataResponse {
//        return findResource(provider)
//    }


    /**
     * Resolves full row data for bulk export — the actual stored field values
     * (same shape the list/detail views use via ResourceRow), not the
     * summarized {id, label} ObjectResponse used for relation lookups.
     *
     * Empty ids means "export everything" for this resource.
     *
     * NOTE: this loads the full result set into memory. Fine for moderate
     * table sizes; if any resource can grow into the hundreds of thousands of
     * rows, this should be swapped for a streaming/cursor-based export instead.
     */
    fun getResourceDataForExport(name: String, ids: List<String>): List<ResourceRow> {
        val resource = findResource(name)

        return if (ids.isEmpty()) {
            resource.getAllRows(
                page = 1,
                size = Int.MAX_VALUE,
                query = null,
                columns = resource.columns,
                sortField = null,
                sortDirection = null
            ).items
        } else {
            ids.mapNotNull { id -> resource.getById(id) }
        }
    }
    
}
