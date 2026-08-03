package controller

import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.actions.KraftActionResponse
import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.api.responses.ResourceDataResponse
import com.kraftadmin.security.AdminSessionStore
import com.kraftadmin.spi.DataSourceInfo
import com.kraftadmin.spi.DataSourceKind
import com.kraftadmin.spi.EntityDiscoveryService
import com.kraftadmin.spi.RuntimeInfo
import com.kraftadmin.ui_descriptors.KraftAdminDescriptor
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.utils.validation.ValidationResponse
import config.KraftAdminProperties
import discovery.discoverer.environment.SpringBootEnvironmentProvider
import events.SpringKraftCustomActionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import persistence.metrics.KraftMetricService
import security.SecurityProviderChain

/**
 * Unit tests for [KraftAdminSpringbootMetaController].
 * */
class KraftAdminSpringbootMetaControllerTest {

    private lateinit var descriptorFactory: KraftAdminDescriptorFactory
    private lateinit var chain: SecurityProviderChain
    private lateinit var properties: KraftAdminProperties
    private lateinit var customActionService: SpringKraftCustomActionService
    private lateinit var entityDiscoveryService: EntityDiscoveryService
    private lateinit var metricService: KraftMetricService
    private lateinit var environment: SpringBootEnvironmentProvider
    private lateinit var sessionStore: AdminSessionStore

    private lateinit var controller: KraftAdminSpringbootMetaController

    @BeforeEach
    fun setUp() {
        descriptorFactory = mockk(relaxed = true)
        chain = mockk(relaxed = true)
        properties = mockk(relaxed = true)
        customActionService = mockk(relaxed = true)
        entityDiscoveryService = mockk(relaxed = true)
        metricService = mockk(relaxed = true)
        environment = mockk(relaxed = true)
        sessionStore = mockk(relaxed = true)

        controller = KraftAdminSpringbootMetaController(
            descriptorFactory = descriptorFactory,
            chain = chain,
            properties = properties,
            customActionService = customActionService,
            entityDiscoveryService = entityDiscoveryService,
            metricService = metricService,
            environment = environment,
            sessionStore = sessionStore
        )
    }

    // GET /dashboard
    @Nested
    inner class DashboardOverview {

        @Test
        fun `aggregates stats, features, system status and metrics into the response`() {
            every { descriptorFactory.getRegisteredResourceNames() } returns listOf("users", "orders")
            every { descriptorFactory.getTotalCountForResource("users") } returns 10L
            every { descriptorFactory.getTotalCountForResource("orders") } returns 5L
            every { sessionStore.activeCount() } returns 3

            every { properties.title } returns "My Admin"
            every { properties.telemetryConfig.enabled } returns true
            every { properties.telemetryConfig.cloudUrl } returns "https://telemetry.example.com"

            every { entityDiscoveryService.discoverAll() } returns emptySet()
            every { metricService.compute(any()) } returns emptyList()

            val runtimeInfo = mockk<RuntimeInfo>(relaxed = true)
            every { runtimeInfo.uptimeSeconds } returns 1234L
            every { runtimeInfo.javaVersion } returns "21"
            every { runtimeInfo.appVersion } returns "1.0.0"
            every { environment.getRuntimeInfo() } returns runtimeInfo
            every { environment.getEnvironmentName() } returns "production"
            every { environment.isProduction() } returns true

            // Keep a handle on the exact stubbed object so the assertion below
            // compares against the real DataSourceInfo shape the controller
            // receives, not a display string.
            val dataSource = DataSourceInfo(
                name = "primary-db",
                kind = DataSourceKind.UNKNOWN,
                productName = "",
                productVersion = "",
                driverOrClientName = "",
                connectionString = "",
                poolType = "",
                activeConnections = 0,
                idleConnections = 0,
                maxPoolSize = 10,
                reachable = true,
                extra = emptyMap()
            )
            every { environment.getDataSources() } returns listOf(dataSource)

            val response = controller.getDashboardOverview()

            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!

            assertEquals("My Admin", body.title)
            assertTrue(body.welcomeMessage.contains("My Admin"))

            // Stats: total records = sum of per-resource counts, resource count, active sessions
            val statLabels = body.stats.map { it.label }
            assertTrue(statLabels.contains("Total Managed Records"))
            assertTrue(statLabels.contains("Resources Registered"))
            assertTrue(statLabels.contains("Active Sessions"))

            val totalRecordsStat = body.stats.first { it.label == "Total Managed Records" }
            assertEquals("15", totalRecordsStat.value)

            val resourceCountStat = body.stats.first { it.label == "Resources Registered" }
            assertEquals("2", resourceCountStat.value)

            val activeSessionsStat = body.stats.first { it.label == "Active Sessions" }
            assertEquals("3", activeSessionsStat.value)

            // System status
            assertEquals("production", body.systemStatus.environment)
            assertTrue(body.systemStatus.isProduction)
            assertEquals(2, body.systemStatus.totalEntitiesTracked)
            assertEquals(listOf(dataSource), body.systemStatus.dataSources)
            assertEquals(1234L, body.systemStatus.uptimeSeconds)
            assertEquals("21", body.systemStatus.javaVersion)
            assertEquals("1.0.0", body.systemStatus.appVersion)

            // Features: telemetry active, auditing active, custom actions pending
            val telemetryFeature = body.features.first { it.name == "Telemetry & BI" }
            assertEquals("Active", telemetryFeature.status)
            assertTrue(telemetryFeature.description.contains("https://telemetry.example.com"))

            val auditingFeature = body.features.first { it.name == "BaseEntity Auditing" }
            assertEquals("Active", auditingFeature.status)
            assertNull(auditingFeature.unlockCriteria)

            val customActionsFeature = body.features.first { it.name == "Custom Actions" }
            assertEquals("Pending", customActionsFeature.status)
        }

        @Test
        fun `marks telemetry feature disabled when telemetry config is off`() {
            every { descriptorFactory.getRegisteredResourceNames() } returns emptyList()
            every { sessionStore.activeCount() } returns 0
            every { properties.title } returns "Admin"
            every { properties.telemetryConfig.enabled } returns false
            every { properties.telemetryConfig.cloudUrl } returns "https://telemetry.example.com"
            every { entityDiscoveryService.discoverAll() } returns emptySet()
            every { metricService.compute(any()) } returns emptyList()

            val runtimeInfo = mockk<RuntimeInfo>(relaxed = true)
            every { environment.getRuntimeInfo() } returns runtimeInfo
            every { environment.getEnvironmentName() } returns "dev"
            every { environment.isProduction() } returns false
            every { environment.getDataSources() } returns emptyList()

            val response = controller.getDashboardOverview()

            val telemetryFeature = response.body!!.features.first { it.name == "Telemetry & BI" }
            assertEquals("Disabled", telemetryFeature.status)
        }

        @Test
        fun `sums total records across zero resources without error`() {
            every { descriptorFactory.getRegisteredResourceNames() } returns emptyList()
            every { sessionStore.activeCount() } returns 0
            every { properties.title } returns "Admin"
            every { properties.telemetryConfig.enabled } returns false
            every { properties.telemetryConfig.cloudUrl } returns ""
            every { entityDiscoveryService.discoverAll() } returns emptySet()
            every { metricService.compute(any()) } returns emptyList()

            val runtimeInfo = mockk<RuntimeInfo>(relaxed = true)
            every { environment.getRuntimeInfo() } returns runtimeInfo
            every { environment.getEnvironmentName() } returns "dev"
            every { environment.isProduction() } returns false
            every { environment.getDataSources() } returns emptyList()

            val response = controller.getDashboardOverview()

            val totalRecordsStat = response.body!!.stats.first { it.label == "Total Managed Records" }
            assertEquals("0", totalRecordsStat.value)
        }
    }

    // GET /resources/descriptors
    @Test
    fun `descriptor delegates to descriptorFactory with the chain and properties`() {
        val expected = mockk<KraftAdminDescriptor>()
        every { descriptorFactory.create(chain = chain, pConfig = properties) } returns expected

        val result = controller.descriptor()

        assertEquals(expected, result)
        verify(exactly = 1) { descriptorFactory.create(chain = chain, pConfig = properties) }
    }

    // GET /resources/{name}
    @Nested
    inner class GetResourceData {

        @Test
        fun `passes through all query parameters to the descriptor factory`() {
            val expected = mockk<ResourceDataResponse>()
            every {
                descriptorFactory.getResourceData(
                    name = "users",
                    page = 2,
                    size = 50,
                    query = "smith",
                    sortField = "createdAt",
                    sortDirection = "desc"
                )
            } returns expected

            val result = controller.getResourceData(
                resourceName = "users",
                page = 2,
                size = 50,
                q = "smith",
                sortField = "createdAt",
                sortDirection = "desc"
            )

            assertEquals(expected, result)
        }

        @Test
        fun `uses default page and size when not supplied`() {
            val expected = mockk<ResourceDataResponse>()
            every {
                descriptorFactory.getResourceData(
                    name = "users",
                    page = 1,
                    size = 20,
                    query = null,
                    sortField = null,
                    sortDirection = null
                )
            } returns expected

            // Defaults are applied by Spring's @RequestParam at the MVC layer;
            // here we call with the documented defaults explicitly to verify
            // the controller forwards them unchanged.
            val result = controller.getResourceData(
                resourceName = "users",
                page = 1,
                size = 20,
                q = null,
                sortField = null,
                sortDirection = null
            )

            assertEquals(expected, result)
        }
    }

    // GET /resources/{name}/{id}
    @Test
    fun `details wraps the resource row in a successful operation response`() {
        val row = mockk<ResourceRow>()
        every { descriptorFactory.getResourceDetailsData("users", "42") } returns row

        val response = controller.details("users", "42")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.success)
        assertEquals("Resource details returned", response.body!!.message)
        assertEquals(row, response.body!!.data)
    }

    // POST /resources/{name}  (save)
    @Nested
    inner class Save {

        @Test
        fun `returns 200 with an Updated message when the payload has a non-zero numeric id`() {
            val validationResult = mockk<Map<String, Any>>(relaxed = true)
            every { descriptorFactory.validateAndSave("users", any()) } returns
                    relaxedSaveResult(success = true, data = validationResult)

            val response = controller.save("users", mapOf("id" to 7, "name" to "Ann"))

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body!!.success)
            assertEquals("Updated users successfully.", response.body!!.message)
        }

        @Test
        fun `treats a numeric id of zero as a create, not an update`() {
            every { descriptorFactory.validateAndSave("users", any()) } returns
                    relaxedSaveResult(success = true)

            val response = controller.save("users", mapOf("id" to 0, "name" to "Ann"))

            assertEquals("Saved users successfully.", response.body!!.message)
        }

        @Test
        fun `treats a missing id as a create`() {
            every { descriptorFactory.validateAndSave("users", any()) } returns
                    relaxedSaveResult(success = true)

            val response = controller.save("users", mapOf("name" to "Ann"))

            assertEquals("Saved users successfully.", response.body!!.message)
        }

        @Test
        fun `treats a blank string id as a create`() {
            every { descriptorFactory.validateAndSave("users", any()) } returns
                    relaxedSaveResult(success = true)

            val response = controller.save("users", mapOf("id" to "   ", "name" to "Ann"))

            assertEquals("Saved users successfully.", response.body!!.message)
        }

        @Test
        fun `treats a non-blank string id as an update`() {
            every { descriptorFactory.validateAndSave("users", any()) } returns
                    relaxedSaveResult(success = true)

            val response = controller.save("users", mapOf("id" to "abc-123", "name" to "Ann"))

            assertEquals("Updated users successfully.", response.body!!.message)
        }

        @Test
        fun `returns 422 with validation errors when save fails`() {
            every { descriptorFactory.validateAndSave("users", any()) } returns
                    relaxedSaveResult(success = false, errors = mapOf("name" to listOf("must not be blank")))

            val response = controller.save("users", mapOf("name" to ""))

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
            assertFalse(response.body!!.success)
            assertEquals("Validation failed.", response.body!!.message)
            assertEquals(mapOf("name" to listOf("must not be blank")), response.body!!.errors)
        }

        @Test
        fun `forwards the exact payload it was given to validateAndSave`() {
            val payloadSlot = slot<Map<String, Any?>>()
            every {
                descriptorFactory.validateAndSave("users", capture(payloadSlot))
            } returns relaxedSaveResult(success = true)

            val payload = mapOf("id" to 1, "name" to "Ann", "active" to true)
            controller.save("users", payload)

            assertEquals(payload, payloadSlot.captured)
        }
    }

    // DELETE /resources/{name}/{id}
    @Test
    fun `delete returns whatever the descriptor factory produces, wrapped as 200 OK`() {
        val deleteResult = mockk<KraftOperationResponse<Unit>>()
        every { descriptorFactory.deleteResource("users", "42") } returns deleteResult

        val response = controller.delete("users", "42")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(deleteResult, response.body)
    }

    // GET /resources/{name}/lookup
    @Nested
    inner class Lookup {

        @Test
        fun `looks up by ids when ids param is present, ignoring search`() {
            val expected = listOf(mockk<ObjectResponse>())
            every {
                descriptorFactory.getLookupDataByIds("users", listOf("1", "2", "3"))
            } returns expected

            val response = controller.lookup(name = "users", search = "irrelevant", ids = "1, 2 ,3")

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(expected, response.body)
            verify(exactly = 0) { descriptorFactory.getLookupData(any(), any()) }
        }

        @Test
        fun `filters out blank id segments`() {
            val expected = listOf(mockk<ObjectResponse>())
            every {
                descriptorFactory.getLookupDataByIds("users", listOf("1", "2"))
            } returns expected

            controller.lookup(name = "users", search = "", ids = "1,,2,")

            verify { descriptorFactory.getLookupDataByIds("users", listOf("1", "2")) }
        }

        @Test
        fun `falls back to search-based lookup when ids is null or blank`() {
            val expected = listOf(mockk<ObjectResponse>())
            every { descriptorFactory.getLookupData("users", "ann") } returns expected

            val nullIdsResponse = controller.lookup(name = "users", search = "ann", ids = null)
            assertEquals(expected, nullIdsResponse.body)

            every { descriptorFactory.getLookupData("users", "ann") } returns expected
            val blankIdsResponse = controller.lookup(name = "users", search = "ann", ids = "   ")
            assertEquals(expected, blankIdsResponse.body)
        }
    }

    // POST /resources/{resource}/id/{id}/action/{actionName}
    @Test
    fun `handleCustomAction delegates to the custom action service and returns its response`() {
        val expected = mockk<KraftActionResponse>()
        val input = mapOf("reason" to "test")
        every {
            customActionService.execute("users", "42", "activate", input)
        } returns expected

        val result = controller.handleCustomAction(
            resource = "users",
            id = "42",
            actionName = "activate",
            input = input
        )

        assertEquals(expected, result)
    }

    // GET /  (performBulkAction)
    @Test
    fun `performBulkAction completes without throwing`() {
        // Currently just logs; this test guards against regressions that
        // introduce a throwing side effect on this endpoint.
        runBlocking {
            controller.performBulkAction()
        }
    }

    // Helpers
    private fun relaxedSaveResult(
        success: Boolean,
        data: Map<String, Any>? = null,
        errors: Map<String, List<String>> = emptyMap()
    ): ValidationResponse {
        return ValidationResponse(
            success = success,
            data = data,
            errors = errors
        )
    }
}