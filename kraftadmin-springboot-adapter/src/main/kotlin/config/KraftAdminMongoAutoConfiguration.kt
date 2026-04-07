package com.kraftadmin.config

import com.kraftadmin.spi.EntityDiscoverer
import com.kraftadmin.discovery.MongoDocumentDiscoverer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.data.mongodb.core.mapping.MongoMappingContext

@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.data.mongodb.core.MongoTemplate"])
class KraftAdminMongoAutoConfiguration {

    private val logger = LoggerFactory.getLogger(javaClass)


    @Bean
    @ConditionalOnBean(MongoMappingContext::class)
    fun mongoEntityDiscoverer(
        applicationContext: ApplicationContext
    ): EntityDiscoverer {
        logger.info("🔧 Registering MongoDB Document Discoverer")
        return MongoDocumentDiscoverer(applicationContext)
    }
}

