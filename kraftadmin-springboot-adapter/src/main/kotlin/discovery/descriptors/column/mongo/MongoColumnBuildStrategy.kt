package discovery.descriptors.column.mongo

import com.kraftadmin.spi.KraftAdminColumn
import discovery.descriptors.column.ColumnBuildStrategy
import discovery.descriptors.column.jpa.ValidationResolver
import discovery.descriptors.column.resolvers.FileResolver
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class MongoColumnBuildStrategy : ColumnBuildStrategy {

    override fun buildColumns(
        entityClass: KClass<*>,
        properties: List<KProperty1<out Any, *>>
    ): List<KraftAdminColumn> {

        val columnResolver = MongoColumnResolver(
            fileResolver = FileResolver(),
            validationResolver = ValidationResolver(),
            mongoLookupResolver = MongoLookupResolver()
        )

        return properties.mapNotNull { property ->
            columnResolver.resolve(entityClass, property)
        }
    }
}