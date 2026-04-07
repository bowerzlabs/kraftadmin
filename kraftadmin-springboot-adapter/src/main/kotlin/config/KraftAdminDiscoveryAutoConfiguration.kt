package com.kraftadmin.config

import com.kraftadmin.spi.EntityDiscoverer
import com.kraftadmin.discovery.EntityDiscoveryService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration
@AutoConfigureAfter(
    KraftAdminJpaAutoConfiguration::class,
    KraftAdminMongoAutoConfiguration::class
)
class KraftAdminDiscoveryAutoConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun entityDiscoveryService(
        discoverers: List<EntityDiscoverer>
    ): EntityDiscoveryService {
        logger.info("🔧 Creating EntityDiscoveryService")
        logger.info("   Discoverers found: ${discoverers.size}")

        discoverers.forEach { discoverer ->
            logger.info("   - ${discoverer.name} Discoverer")
        }

        if (discoverers.isEmpty()) {
            logger.warn("⚠️  No discoverers registered! Entities won't be found.")
        }

        return EntityDiscoveryService(discoverers)
    }
}