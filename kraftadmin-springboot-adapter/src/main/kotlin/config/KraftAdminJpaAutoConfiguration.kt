package config

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.EntityDiscoverer
import com.kraftadmin.spi.KraftMetricProvider
import discovery.discoverer.jpa.JpaEntityDiscoverer
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import persistence.jpa.metrics.JpaMetricProvider
import persistence.jpa.provider.JpaKraftDataProviderFactory

@AutoConfiguration
@ConditionalOnClass(
    EntityManagerFactory::class,
    EntityManager::class
)
@ConditionalOnProperty(
    prefix = "kraftadmin",
    name = ["enabled"],
    havingValue = "true"
)
class KraftAdminJpaAutoConfiguration {

    private val logger = KraftAdminLogging.logger(javaClass)

    @Bean
    fun jpaEntityDiscoverer(
        applicationContext: ApplicationContext
    ): EntityDiscoverer {
        logger.info("Registering JPA Entity Discoverer")
        return JpaEntityDiscoverer(applicationContext)
    }

    @Bean
    fun jpaDataProviderFactory(
        entityManager: EntityManager
    ): JpaDataProviderFactory {
        return JpaDataProviderFactory(entityManager)
    }

    @Bean
    fun jpaKraftDataProviderFactory(): JpaKraftDataProviderFactory {
        return JpaKraftDataProviderFactory()
    }

    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate {
        return TransactionTemplate(transactionManager)
    }

    @Bean
    @ConditionalOnMissingBean(KraftMetricProvider::class)
    fun jpaKraftMetricProvider(
        entityManager: EntityManager,
        transactionTemplate: TransactionTemplate
    ): KraftMetricProvider {
        logger.info("Registering JPA metric provider")

        return JpaMetricProvider(
            entityManager = entityManager,
            transactionTemplate = transactionTemplate
        )
    }
}

class JpaDataProviderFactory(
    val entityManager: EntityManager
)