package com.kraftadmin.discovery

import com.kraftadmin.spi.EntityDiscoverer
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

class MongoDocumentDiscoverer(
    private val applicationContext: ApplicationContext
) : EntityDiscoverer {

    override val name: String = "Mongo"

    override fun discover(): Set<Class<*>> {
        val mongoContextClass = try {
            Class.forName("org.springframework.data.mongodb.core.mapping.MongoMappingContext")
        } catch (_: ClassNotFoundException) {
            return emptySet()
        }

        val mongoContext = try {
            applicationContext.getBean(mongoContextClass)
        } catch (_: Exception) {
            return emptySet()
        }

        val persistentEntities =
            mongoContextClass.getMethod("getPersistentEntities").invoke(mongoContext) as Iterable<*>

        return persistentEntities.mapNotNull {
            it!!.javaClass.getMethod("getType").invoke(it) as Class<*>
        }.toSet()
    }
}


