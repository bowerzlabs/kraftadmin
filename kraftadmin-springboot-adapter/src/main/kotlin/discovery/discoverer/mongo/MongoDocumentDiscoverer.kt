package discovery.discoverer.mongo

import com.kraftadmin.enums.ProviderType
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.DiscoveredEntity
import com.kraftadmin.spi.EntityDiscoverer
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

class MongoDocumentDiscoverer(
    private val applicationContext: ApplicationContext
) : EntityDiscoverer {

    private val logger = KraftAdminLogging.logger(javaClass)
    override val provider: ProviderType = ProviderType.MONGO

    override fun discover(): Set<DiscoveredEntity<*>> {
        logger.info("MONGO Discoverer - Scanning")
        val mongoContext = applicationContext.getBean(MongoMappingContext::class.java)

        val entities = mongoContext.persistentEntities
            .filter { it.type.isAnnotationPresent(Document::class.java) }
            .map { entity ->
                DiscoveredEntity(
                    entityClass = entity.type,
                    provider = ProviderType.MONGO
                )
            }
            .toSet()

        logger.info("MONGO Discoverer - Found ${entities.size} document(s), skipped ${mongoContext.persistentEntities.count() - entities.size} embedded/non-document type(s)")
        return entities
    }
}