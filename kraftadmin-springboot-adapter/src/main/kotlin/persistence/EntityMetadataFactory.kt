package persistence

import com.kraftadmin.enums.ProviderType
import com.kraftadmin.spi.KraftEntityMetadata
import persistence.jpa.metadata.JpaEntityMetadata
import persistence.mongo.metadata.MongoEntityMetadata
import kotlin.reflect.KClass

object EntityMetadataFactory {

    fun <T : Any> create(providerType: ProviderType, entityClass: KClass<T>): KraftEntityMetadata<T> =
        when (providerType) {
            ProviderType.JPA -> JpaEntityMetadata(entityClass)
            ProviderType.MONGO -> MongoEntityMetadata(entityClass)
            ProviderType.R2DBC -> error(
                "R2DBC metadata support is not implemented yet for ${entityClass.simpleName}"
            )
        }
}