package com.kraftadmin.ui_descriptors

import api.responses.ResourceDataResponse
import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.config.KraftAdminRuntimeConfig
import com.kraftadmin.spi.EntityDiscoverer
import com.kraftadmin.spi.KraftAdminResource
import security.SecurityProviderChain
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.utils.validation.KraftValidationExtractor
import com.kraftadmin.utils.validation.ValidationResponse
import config.KraftAdminPropertiesConfig
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class KraftAdminDescriptorFactory(
    private val runtimeConfig: KraftAdminRuntimeConfig,
    private val validationExtractor: KraftValidationExtractor,
    private val environmentProvider: KraftEnvironmentProvider,
    private val entityDiscoverer: EntityDiscoverer
) {

    private val logger = LoggerFactory.getLogger(KraftAdminDescriptorFactory::class.java)

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
        size: Int = 20
    ): ResourceDataResponse {
        val resource = findResource(name)
        val columns = resource.columns // Assuming columns comes from the SPI resource

        // Fetch the data using the pagination logic we built
        val pagedData = resource.getAllRows(page, size, columns)

        //  Map the resource to its Descriptor and attach the live data
        val descriptor = ResourceDescriptor(
            name = resource.name,
            label = resource.label,
            customActions = resource.customActions,
            columns = columns.map { it.toDescriptor() },
            data = pagedData
        )

        return ResourceDataResponse(resource = descriptor)
    }

    /**
     * The primary entry point for saving data with a "Pre-flight" validation check.
     */
    fun validateAndSave(name: String, payload: Map<String, Any?>): ValidationResponse {
        val resource = findResource(name)
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
            logger.warn("Save blocked by validation errors: $errors")
            return ValidationResponse(success = false, errors = errors)
        }

        // Only save if errors is empty
        val savedData = resource.save(name, payload)
        return ValidationResponse(success = true, data = savedData)
    }

    fun getResourceDetailsData(name: String, id: String): ResourceRow? {
         val resource = findResource(name)
        return resource.getById(id)
    }

    // Delete
    fun deleteResource(name: String, id: String) {
        val resource = findResource(name)
        resource.delete(id)
    }

    fun getLookupData(resourceName: String, columnName: String, query: String?): List<ObjectResponse> {
        val lookup = LookupDescriptor(targetEntity = resourceName, searchField = columnName, lookupKey = columnName)
        // 1. Use your existing helper to find the resource
        val resource = findResource(resourceName)

        // We cast to JpaDataProvider<*> to access the lookup logic we wrote earlier
        val provider = resource.dataProvider
            ?: throw IllegalStateException("Resource '$resourceName' does not use any KraftDataProvider.")

        return provider.getLookupData(
            lookup = lookup,
            limit = 20,
            searchQuery = query
        )
    }

    // Build the lookup map once (or on demand)
    private val resourceRegistry: Map<String, KClass<*>> by lazy {
        entityDiscoverer.discover()
            .map { it.kotlin }
            .associateBy { it.simpleName ?: "" }
    }

    /**
     * Maps the string name (e.g., "Venue") back to the annotated Kotlin Class.
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
        // We assume your SPI 'KraftAdminResource' or its DataProvider
        // has a count() method.
        return resource.countAll(name) ?: 0
    }

    /**
     * Returns the underlying SPI resource if needed for metadata checks.
     */
//    fun getResourceData(name: String): ResourceDataResponse {
//        return findResource(name)
//    }
}
