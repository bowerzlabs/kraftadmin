package com.kraftadmin.discovery

import com.kraftadmin.spi.EntityDiscoverer
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class EntityDiscoveryService(
    private val discoverers: List<EntityDiscoverer>
) : EntityDiscoverer {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun discoverAll(): Set<Class<*>> {
        val allEntities = mutableSetOf<Class<*>>()
        discoverers.forEach { discoverer ->
            try {
                val discovered = discoverer.discover()
                allEntities.addAll(discovered)
            } catch (e: Exception) {
                logger.error("Error in discoverer ${discoverer.name}", e)
            }
        }
        return allEntities
    }

    override fun discover(): Set<Class<*>> {
        val allEntities = mutableSetOf<Class<*>>()
        discoverers.forEach { discoverer ->
            try {
                val discovered = discoverer.discover()
                allEntities.addAll(discovered)
            } catch (e: Exception) {
                logger.error("Error in discoverer ${discoverer.name}", e)
            }
        }
        return allEntities
    }

    override val name: String = "Springboot discovery"

}
