package persistence.mongo.provider

import com.kraftadmin.enums.ProviderType
import com.kraftadmin.spi.*
import config.KraftAdminProperties
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import persistence.KraftDataProviderFactory

@Component
class MongoKraftDataProviderFactory : KraftDataProviderFactory<Any> {

    override fun supports(providerType: ProviderType) = providerType == ProviderType.MONGO

    override fun create(
        discoveredEntity: DiscoveredEntity<Any>,
        context: ApplicationContext,
        properties: KraftAdminProperties
    ): KraftDataProvider<Any>? {
        val mongoTemplate = context.getBeanProvider(MongoTemplate::class.java).ifAvailable ?: return null
        return MongoDataProvider(
            mongoTemplate = mongoTemplate,
            entityClass = discoveredEntity.entityClass.kotlin,
            applicationContext = context
        )
    }


}



