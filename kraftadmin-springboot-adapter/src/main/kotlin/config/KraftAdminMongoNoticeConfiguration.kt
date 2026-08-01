package config

import com.kraftadmin.logging.KraftAdminLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.util.ClassUtils

/**
 * Runs whenever KraftAdmin itself is enabled (kraftadmin.enabled=true),
 * independently of whether Mongo support was opted into. Its only job
 * is to tell people with MongoTemplate on the classpath that Mongo
 * resources aren't auto-discovered yet, rather than leaving them to
 * wonder why their @Document entities never showed up.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftAdminMongoNoticeAutoConfiguration {

    private val logger = KraftAdminLogging.logger(javaClass)

    init {
        val mongoPresent = ClassUtils.isPresent(
            "org.springframework.data.mongodb.core.MongoTemplate",
            javaClass.classLoader
        )

        if (mongoPresent) {
            logger.info(
                "KraftAdmin: MongoDB detected on the classpath. Mongo resource support is not " +
                        "enabled by default in this release — JPA support is being stabilized first. " +
                        "Mongo entities will not be discovered or exposed as KraftAdmin resources. " +
                        "Support is planned for a future release; set kraftadmin.mongo.enabled=true " +
                        "to opt in early (unsupported, may be unstable)."
            )
        }
    }
}