package discovery

import com.kraftadmin.discovery.EntityDiscoveryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import com.kraftadmin.spi.EntityDiscoverer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Dummy entity classes for testing metadata tracking
private class MockUserEntity
private class MockPostEntity
private class MockTenantEntity

class EntityDiscoveryServiceTest {

    @Test
    fun `discoverAll should aggregate unique entities from all discoverers`() {
        // Arrange
        val discoverer1 = mockk<EntityDiscoverer> {
            every { name } returns "JpaDiscoverer"
            every { discover() } returns setOf(MockUserEntity::class.java, MockPostEntity::class.java)
        }
        val discoverer2 = mockk<EntityDiscoverer> {
            every { name } returns "MongoDiscoverer"
            every { discover() } returns setOf(MockPostEntity::class.java, MockTenantEntity::class.java)
        }

        val discoveryService = EntityDiscoveryService(listOf(discoverer1, discoverer2))

        // Act
        val result = discoveryService.discoverAll()

        // Assert
        assertEquals(3, result.size, "Should aggregate all unique entities across discoverers")
        assertTrue(result.containsAll(listOf(MockUserEntity::class.java, MockPostEntity::class.java, MockTenantEntity::class.java)))
    }

    @Test
    fun `discoverAll should handle empty discoverer list gracefully`() {
        // Arrange
        val discoveryService = EntityDiscoveryService(emptyList())

        // Act
        val result = discoveryService.discoverAll()

        // Assert
        assertTrue(result.isEmpty(), "Result should be empty when no discoverers are registered")
    }

    @Test
    fun `discoverAll should continue processing if a discoverer throws an exception`() {
        // Arrange
        val failingDiscoverer = mockk<EntityDiscoverer> {
            every { name } returns "BrokenDiscoverer"
            every { discover() } throws RuntimeException("Database connection failure")
        }
        val healthyDiscoverer = mockk<EntityDiscoverer> {
            every { name } returns "HealthyDiscoverer"
            every { discover() } returns setOf(MockUserEntity::class.java)
        }

        val discoveryService = EntityDiscoveryService(listOf(failingDiscoverer, healthyDiscoverer))

        // Act
        val result = discoveryService.discoverAll()

        // Assert
        assertEquals(1, result.size, "Should skip the failing discoverer but still process the healthy one")
        assertTrue(result.contains(MockUserEntity::class.java))
        verify(exactly = 1) { failingDiscoverer.discover() }
        verify(exactly = 1) { healthyDiscoverer.discover() }
    }

    @Test
    fun `discover override should execute identical collection aggregation logic`() {
        // Arrange
        val discoverer = mockk<EntityDiscoverer> {
            every { name } returns "CustomDiscoverer"
            every { discover() } returns setOf(MockTenantEntity::class.java)
        }
        val discoveryService = EntityDiscoveryService(listOf(discoverer))

        // Act
        val result = discoveryService.discover()

        // Assert
        assertEquals("Springboot discovery", discoveryService.name)
        assertEquals(1, result.size)
        assertTrue(result.contains(MockTenantEntity::class.java))
    }

}