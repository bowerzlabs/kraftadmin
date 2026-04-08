package com.kraftadmin.controller

import api.responses.ResourceDataResponse
import api.utils.ResourceRow
import com.kraftadmin.annotations.KraftAdminCustomAction
import com.kraftadmin.api.responses.DashboardStat
import com.kraftadmin.api.responses.KraftDashboardResponse
import com.kraftadmin.api.responses.LibraryFeature
import com.kraftadmin.api.responses.SystemStatus
import com.kraftadmin.config.SpringKraftAdminProperties
import com.kraftadmin.security.SecurityProviderChain
import com.kraftadmin.ui_descriptors.KraftAdminDescriptor
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.utils.custom_actions.KraftActionHandler
import com.kraftadmin.utils.custom_actions.KraftActionResponse
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findAnnotations


@RestController
@RequestMapping("\${kraftadmin.base-path:/admin}/api")
class KraftAdminSpringbootMetaController(
    private val descriptorFactory: KraftAdminDescriptorFactory,
    private val chain: SecurityProviderChain,
    private val properties: SpringKraftAdminProperties,
    private val applicationContext: ApplicationContext
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/dashboard")
    fun getDashboardOverview(): ResponseEntity<KraftDashboardResponse> {
        // 1. Calculate Entity Counts dynamically
        val resourceNames = descriptorFactory.getRegisteredResourceNames()

        // sumOf usually returns Int or Long; ensure your factory returns a numeric type
        val totalEntitiesCount = resourceNames.sumOf { name ->
            descriptorFactory.getTotalCountForResource(name)
        }

        // 2. Map Stats Cards
        val stats = listOf(
            DashboardStat("Total Managed Records", totalEntitiesCount.toString(), "database"),
            DashboardStat("Resources Registered", resourceNames.size.toString(), "layers"),
            DashboardStat("Active Sessions", "1", "users")
        )

        // 3. Use the dynamic check instead of hardcoded list
        val libraryFeatures = checkFeatureStatus()

        val response = KraftDashboardResponse(
            title = properties.title,
            welcomeMessage = "Welcome to the ${properties.title} command center.",
            stats = stats,
            features = libraryFeatures,
            systemStatus = SystemStatus(
                environment = "Development", // You can pull this from Spring Profile if needed
                databaseType = "H2 / R2DBC",
                totalEntitiesTracked = resourceNames.size
            )
        )

        return ResponseEntity.ok(response)
    }

    /**
     * Provides the UI Schema (Columns, InputTypes, Labels)
     * used by the Svelte form to render inputs.
     */
    @GetMapping("/resources/descriptors")
    fun descriptor(): KraftAdminDescriptor = descriptorFactory.create(chain = chain, pConfig = properties)

    /**
     * List view data.
     */
    @GetMapping("/resources/{name}")
    fun getResourceData(
        @PathVariable(name = "name") resourceName: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResourceDataResponse {
        // Simply delegate to the factory
        return descriptorFactory.getResourceData(resourceName, page, size)
    }

    /**
     * Fetch a single entity for editing.
     * The Map returned here must match the Svelte formData structure.
     */
    @GetMapping("/resources/{name}/{id}")
    fun details(@PathVariable name: String, @PathVariable id: String): ResponseEntity<ResourceRow> {
        // getResourceDetailsData now returns ResourceRow instead of Map
        val data = descriptorFactory.getResourceDetailsData(name, id)
        return data?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    /**
     * The main Save/Update entry point.
     * This triggers the conversion logic (including LocalTime/LocalDate parsing).
     */
    @PostMapping("/resources/{name}")
    fun save(@PathVariable name: String, @RequestBody data: Map<String, Any?>): ResponseEntity<Any> {
        logger.info("Validating and saving resource: $name with data: $data")

        val result = descriptorFactory.validateAndSave(name, data)

        return if (result.success) {
            ResponseEntity.status(HttpStatus.CREATED).body(result.data)
        } else {
            // Return 422 with a map of field names -> list of error messages
            ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result.errors)
        }
    }

    @DeleteMapping("/resources/{name}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable name: String, @PathVariable id: String) {
        descriptorFactory.deleteResource(name, id)
    }

    /**
     * Real-time lookup for RELATION/MULTI_SELECT types.
     * Svelte hits this as the user types in a lookup field.
     */
    @GetMapping("/resources/{name}/lookup/{columnName}")
    fun lookup(
        @PathVariable name: String,
        @PathVariable columnName: String,
        @RequestParam(required = false, defaultValue = "") search: String
    ): ResponseEntity<List<api.utils.ObjectResponse>> {
        logger.debug("Lookup request: Resource=$name, Column=$columnName, Query=$search")
        val results = descriptorFactory.getLookupData(name, columnName, search)
        return ResponseEntity.ok(results)
    }

    @PostMapping("/{resourceName}/{id}/action/{slug}")
    fun executeAction(
        @PathVariable resourceName: String,
        @PathVariable id: String,
        @PathVariable slug: String
    ): ResponseEntity<Map<String, String>> {
        val resource = descriptorFactory.getResourceData(resourceName)
        val entity = resource.resource

        // The library looks up the lambda function registered by the parent app
//        val result = resource.executeAction(slug, entity)
        val result = "Action performed"

        return ResponseEntity.ok(mapOf("message" to result))
    }


    @PostMapping("/resources/{resource}/id/{id}/action/{actionName}")
    fun handleCustomAction(
        @PathVariable resource: String,
        @PathVariable id: String,
        @PathVariable actionName: String,
        @RequestBody params: Map<String, Any?>
    ): KraftActionResponse {

        //  Get the DTO for the UI (this is for the handler to use)
        val resourceRow = descriptorFactory.getResourceDetailsData(resource, id)
            ?: throw IllegalArgumentException("Resource $resource with id $id not found")

        //  Find the actual Entity Class using your registry
        // You need a way to get the KClass from the string name "Venue"
        val entityClass = descriptorFactory.getEntityClassForResource(resource)
            ?: throw IllegalArgumentException("Could not find domain class for $resource")

        // Find the annotation on the Domain Class (not the ResourceRow)
        val annotation = entityClass.findAnnotations<KraftAdminCustomAction>()
            .firstOrNull { it.name == actionName }
            ?: throw IllegalArgumentException("Action $actionName not defined for $resource")

        //  Resolve the Bean and Execute
        val handler = applicationContext.getBean(annotation.handler.java)

        // Pass the 'values' map or the ID to the handler
        return handler.execute(resourceRow, params)
    }

    //     bulk actions controller ie export, print, import etc
    @GetMapping("/")
    suspend fun performBulkAction(

    ){
       logger.info("performing bulk action")

    }

    private fun checkFeatureStatus(): List<LibraryFeature> {
        val features = mutableListOf<LibraryFeature>()

        // Check 1: Telemetry (Dynamic based on YAML/JSON properties)
        features.add(LibraryFeature(
            name = "Telemetry & BI",
            description = "Streaming system events to ${properties.telemetryConfig.cloudUrl}",
            status = if (properties.telemetryConfig.enabled) "Active" else "Disabled",
            unlockCriteria = "Set 'kraftadmin.telemetry-config.enabled: true' in YAML"
        ))

        // Check 2: Database Auditing (Verified by your infra package)
        features.add(LibraryFeature(
            name = "BaseEntity Auditing",
            description = "Automated tracking of created_at and updated_at fields via bowerzlabs.evry.infra.db.",
            status = "Active",
            unlockCriteria = null
        ))

        // Check 3: Custom Actions (Dynamic discovery)
        val hasCustomActions = descriptorFactory.getRegisteredResourceNames()
            .any { name -> descriptorFactory.getResourceData(name).resource.customActions.isNotEmpty() }

        features.add(LibraryFeature(
            name = "Custom Actions",
            description = "Domain-specific logic triggered via @KraftAdminCustomAction",
            status = if (hasCustomActions) "Active" else "Pending",
            unlockCriteria = "Add @KraftAdminCustomAction to your Domain Entities"
        ))

        return features
    }

}

// Simple Exception Handler for cleaner API responses
@ResponseStatus(HttpStatus.NOT_FOUND)
class ResourceNotFoundException(message: String) : RuntimeException(message)
