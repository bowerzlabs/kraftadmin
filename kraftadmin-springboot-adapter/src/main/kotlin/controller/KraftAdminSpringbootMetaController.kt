package controller

import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.actions.KraftActionResponse
import com.kraftadmin.api.responses.DashboardStat
import com.kraftadmin.api.responses.KraftDashboardResponse
import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.api.responses.LibraryFeature
import com.kraftadmin.api.responses.ResourceDataResponse
import com.kraftadmin.api.responses.SystemStatus
import com.kraftadmin.ui_descriptors.KraftAdminDescriptor
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.persistence.metrics.KraftMetricService
import com.kraftadmin.spi.EntityDiscoveryService
import config.KraftAdminProperties
import discovery.metrics.MetricDiscoverer
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import events.SpringKraftCustomActionService
import security.SecurityProviderChain

@RestController
@RequestMapping("\${kraftadmin.base-path:/admin}/api")
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftAdminSpringbootMetaController(
    private val descriptorFactory: KraftAdminDescriptorFactory,
    private val chain: SecurityProviderChain,
    private val properties: KraftAdminProperties,
    private val customActionService: SpringKraftCustomActionService,
    private val entityDiscoveryService: EntityDiscoveryService,
    private val metricService: KraftMetricService
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    @GetMapping("/dashboard")
    fun getDashboardOverview(): ResponseEntity<KraftDashboardResponse> {
        val resourceNames = descriptorFactory.getRegisteredResourceNames()
        val totalEntitiesCount = resourceNames.sumOf { name ->
            descriptorFactory.getTotalCountForResource(name)
        }

        val stats = listOf(
            DashboardStat("Total Managed Records", totalEntitiesCount.toString(), "database"),
            DashboardStat("Resources Registered", resourceNames.size.toString(), "layers"),
            DashboardStat("Active Sessions", "1", "users")
        )

        val discoveredEntities = entityDiscoveryService.discoverAll() // reuse existing discovery
        val discoveredMetrics = MetricDiscoverer.discover(discoveredEntities)
        val metrics = metricService.compute(discoveredMetrics)

        val libraryFeatures = checkFeatureStatus()

        val response = KraftDashboardResponse(
            title = properties.title,
            welcomeMessage = "Welcome to the ${properties.title} admin dashboard.",
            stats = stats,
            features = libraryFeatures,
            systemStatus = SystemStatus(
                environment = "Development",
                databaseType = "H2 / R2DBC",
                totalEntitiesTracked = resourceNames.size
            ),
            metrics = metrics
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
     * List view data. Any failure (unknown resource, DB error, etc.)
     * propagates to KraftAdminExceptionHandler — no local try/catch needed.
     */
    @GetMapping("/resources/{name}")
    fun getResourceData(
        @PathVariable(name = "name") resourceName: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sortField: String?,
        @RequestParam(required = false) sortDirection: String?
    ): ResourceDataResponse {
        logger.info("Fetching resource: {}, page: {}, sortField: {}, sortDirection: {}", resourceName, page, sortField, sortDirection)
        return descriptorFactory.getResourceData(
            name = resourceName,
            page = page,
            size = size,
            query = q,
            sortField = sortField,
            sortDirection = sortDirection,
        )
    }

    /**
     * Fetch a single entity for editing.
     * The Map returned here must match the Svelte formData structure.
     * Failure propagates to KraftAdminExceptionHandler with the correct
     * HTTP status (e.g. 404 for not_found) — never swallowed into a 200.
     */
    @GetMapping("/resources/{name}/{id}")
    fun details(
        @PathVariable name: String,
        @PathVariable id: String
    ): ResponseEntity<KraftOperationResponse<ResourceRow>> {
        logger.info("fetching resource details for $name with id $id")
        val data = descriptorFactory.getResourceDetailsData(name, id)
        return ResponseEntity.ok(
            KraftOperationResponse(
                success = true,
                message = "Resource details returned",
                data = data,
            )
        )
    }

    /**
     * The main Save/Update entry point.
     * Validation failure (result.success == false) is a normal return value,
     * not an exception — that branch stays local. Genuine exceptions (e.g.
     * PersistenceException) propagate to the advice for consistent status/shape.
     */
    @PostMapping("/resources/{name}")
    fun save(
        @PathVariable name: String,
        @RequestBody data: Map<String, Any?>
    ): ResponseEntity<KraftOperationResponse<Any>> {

        logger.info("Saving resource {}", name)

        val id = data["id"]
        val hasId = when (id) {
            null -> false
            is Number -> id.toLong() != 0L
            else -> id.toString().isNotBlank()
        }

        logger.info("id $id, $hasId")
        logger.info("data $data")

        val result = descriptorFactory.validateAndSave(name, data)

        return if (result.success) {
            val message = if (hasId) "Updated $name successfully." else "Saved $name successfully."
            ResponseEntity.ok(
                KraftOperationResponse(
                    success = true,
                    message = message,
                    data = result.data,
                    errors = result.errors
                )
            )
        } else {
            ResponseEntity.unprocessableEntity().body(
                KraftOperationResponse(
                    success = false,
                    message = "Validation failed.",
                    errors = result.errors
                )
            )
        }
    }

    /**
     * Delete resource. Failure propagates to the advice for consistent
     * status/shape (e.g. 409 for foreign_key, 404 for not_found).
     */
    @DeleteMapping("/resources/{name}/{id}")
    fun delete(
        @PathVariable name: String,
        @PathVariable id: String
    ): ResponseEntity<KraftOperationResponse<Unit>> {
        val result = descriptorFactory.deleteResource(name, id)
        return ResponseEntity.ok(result)
    }

    /**
     * Real-time lookup for RELATION/MULTI_SELECT types.
     * Svelte hits this as the user types in a lookup field.
     */
    @GetMapping("/resources/{name}/lookup")
    fun lookup(
        @PathVariable name: String,
        @RequestParam(required = false, defaultValue = "") search: String,
        @RequestParam(required = false) ids: String?
    ): ResponseEntity<List<ObjectResponse>> {
        logger.debug("Lookup: resource=$name search='$search' ids=$ids")

        if (!ids.isNullOrBlank()) {
            val idList = ids.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val results = descriptorFactory.getLookupDataByIds(name, idList)
            return ResponseEntity.ok(results)
        }

        val results = descriptorFactory.getLookupData(name, search)
        return ResponseEntity.ok(results)
    }

    @PostMapping("/resources/{resource}/id/{id}/action/{actionName}")
    fun handleCustomAction(
        @PathVariable resource: String,
        @PathVariable id: String,
        @PathVariable actionName: String,
        @RequestBody input: Any?
    ): KraftActionResponse? {
        logger.info("Action performed: resource {}, id : {}, action: {}, params: {}", resource, id, actionName, input)
        val actionResponse = customActionService.execute(resource, id, actionName, input)
        logger.info("action response: $actionResponse")
        return actionResponse
    }

    @GetMapping("/")
    suspend fun performBulkAction() {
        logger.info("performing bulk action")
    }

    private fun checkFeatureStatus(): List<LibraryFeature> {
        val features = mutableListOf<LibraryFeature>()

        features.add(LibraryFeature(
            name = "Telemetry & BI",
            description = "Streaming system events to ${properties.telemetryConfig.cloudUrl}",
            status = if (properties.telemetryConfig.enabled) "Active" else "Disabled",
            unlockCriteria = "Set 'kraftadmin.telemetry-config.enabled: true' in YAML"
        ))

        features.add(LibraryFeature(
            name = "BaseEntity Auditing",
            description = "Automated tracking of created_at and updated_at fields via bowerzlabs.evry.infra.db.",
            status = "Active",
            unlockCriteria = null
        ))

        features.add(LibraryFeature(
            name = "Custom Actions",
            description = "Domain-specific logic triggered via @KraftAdminCustomAction",
            status = "Pending",
            unlockCriteria = "Add @KraftAdminCustomAction to your Domain Entities"
        ))

        return features
    }

}