package com.kraftadmin.config

import com.kraftadmin.spi.EntityDiscoverer
import com.kraftadmin.discovery.JpaEntityDiscoverer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@AutoConfiguration
//@ConditionalOnClass(name = ["jakarta.persistence.EntityManagerFactory"])
@ConditionalOnClass(jakarta.persistence.EntityManagerFactory::class)
//@ConditionalOnBean(jakarta.persistence.EntityManagerFactory::class)
class KraftAdminJpaAutoConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jpaEntityDiscoverer(
        applicationContext: ApplicationContext
    ): EntityDiscoverer {
        logger.info("Registering JPA Entity Discoverer")
//        logger.info("   EntityManagerFactories: ${entityManagerFactories.size}")
        return JpaEntityDiscoverer(applicationContext)
    }

    @Bean
    fun jpaDataProviderFactory(entityManager: jakarta.persistence.EntityManager): JpaDataProviderFactory {
        logger.info("Registering JPA Data Provider Factory")
        return JpaDataProviderFactory(entityManager)
    }
}

class JpaDataProviderFactory(val entityManager: jakarta.persistence.EntityManager)
