package config

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.EntityDiscoverer
import discovery.discoverer.mongo.MongoDocumentDiscoverer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import persistence.mongo.provider.MongoKraftDataProviderFactory

/**
 * Mongo resource support is not stable yet — JPA is the supported
 * provider for this release. This autoconfiguration is gated behind
 * its own opt-in property (kraftadmin.mongo.enabled), separate from
 * and defaulting to false regardless of kraftadmin.enabled, so it
 * stays inert even when MongoTemplate is on the classpath.
 *
 * Nothing in here runs unless someone explicitly sets
 * kraftadmin.mongo.enabled=true — see KraftAdminMongoNoticeAutoConfiguration
 * for the notice shown to everyone else.
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate::class)
@ConditionalOnProperty(
    prefix = "kraftadmin.mongo",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class KraftAdminMongoAutoConfiguration {

    private val logger = KraftAdminLogging.logger(javaClass)

    init {
        logger.warn(
            "KraftAdmin: Mongo resource support is enabled via kraftadmin.mongo.enabled=true. " +
                    "This is not yet a stable, supported provider — expect rough edges until a future release."
        )
    }

    @Bean
    fun mongoEntityDiscoverer(
        applicationContext: ApplicationContext
    ): EntityDiscoverer {
        logger.info("Registering MongoDB Document Discoverer")
        return MongoDocumentDiscoverer(applicationContext)
    }

    @Bean
    fun mongoKraftDataProviderFactory() : MongoKraftDataProviderFactory{
        return MongoKraftDataProviderFactory()
    }
    
}