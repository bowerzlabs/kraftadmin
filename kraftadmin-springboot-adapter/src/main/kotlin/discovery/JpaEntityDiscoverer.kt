package com.kraftadmin.discovery

import com.kraftadmin.spi.EntityDiscoverer
import jakarta.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext

class JpaEntityDiscoverer(
    private val applicationContext: ApplicationContext
) : EntityDiscoverer {

    private val logger = LoggerFactory.getLogger(javaClass)
    override val name: String = "JPA"

    override fun discover(): Set<Class<*>> {
        logger.info("🔍 JPA Discoverer - Scanning")

        val entityManagerFactories =
            applicationContext.getBeansOfType(EntityManagerFactory::class.java).values.toList()

        val entities = mutableSetOf<Class<*>>()
        entityManagerFactories.forEach { emf ->
            val emfEntities = emf.metamodel.entities.map { it.javaType }
            logger.debug("   EMF ${emf.persistenceUnitUtil}: ${emfEntities.size} entities")
            entities.addAll(emfEntities)
        }

        logger.info("✅ JPA Discoverer - Found ${entities.size} entities")
        return entities
    }
}


