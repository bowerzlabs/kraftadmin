package com.kraftadmin.config

import analytics.AnalyticsProvider
import com.kraftadmin.KraftAdmin
import controller.KraftAdminSpringbootLogController
import com.kraftadmin.controller.KraftAdminSpringbootMetaController
import controller.KraftSpringAnalyticsController
import com.kraftadmin.discovery.EntityDiscoveryService
import com.kraftadmin.discovery.ResourceGenerator
import com.kraftadmin.discovery.SpringBootEnvironmentProvider
import security.SecurityProviderChain
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.util.*
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.utils.files.CloudinaryAdapter
import com.kraftadmin.utils.files.LocalFileSystemAdapter
import com.kraftadmin.utils.validation.KraftValidationExtractor
import config.KraftPulseSpringKraftAdminProperties
import json.KraftJsonSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import util.JacksonKraftJsonSerializer
import util.KraftSpringLoggingService


@AutoConfiguration
@AutoConfigureAfter(
    name = [
        "config.KraftTelemetryAutoConfiguration",
        "config.KraftSpringAuditAutoConfiguration" // Ensure Audit is ready too
    ]
)
@Import(
    KraftAdminJpaAutoConfiguration::class,
    KraftAdminMongoAutoConfiguration::class,
    KraftAdminDiscoveryAutoConfiguration::class,
    KraftAdminWebConfiguration::class,
)
//@EnableConfigurationProperties(KraftPulseSpringKraftAdminProperties::class)
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftAdminSpringBootAutoConfiguration(
    private val properties: KraftPulseSpringKraftAdminProperties,
    private val applicationContext: ApplicationContext,
    private val entityDiscoveryService: EntityDiscoveryService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.info("springboot auto config $properties")
    }

    @Bean
    fun kraftAdminRunner(
        discoveryService: EntityDiscoveryService,
        runtimeConfig: KraftAdminRuntimeConfig,
        context: ApplicationContext
    ): ApplicationRunner {
        return ApplicationRunner {
            val entities = discoveryService.discoverAll()

            entities.forEach {
                logger.info("${it.name}")
            }

            if (entities.isEmpty()) {
                logger.warn("No entities discovered!")
                logger.warn("   Check:")
                logger.warn("   - Are your entities annotated with @Entity?")
                logger.warn("   - Is JPA/MongoDB properly configured?")
                logger.warn("   - Are entities in scanned packages?")
            }

            val resources = entities.map { ResourceGenerator.generate(it, context = context, properties = properties) }
//            logger.info("Generated resources:")

            val config = KraftAdminConfig(
                basePath = properties.basePath,
                title = properties.title,
                discoveredEntities = entities,
                generatedResources = resources
            )

            runtimeConfig.set(config)
            KraftAdmin.start(config)

            logger.info("=" .repeat(70))
            logger.info("KraftAdmin Started")
            logger.info("   Total entities: ${entities.size}")
            logger.info("=" .repeat(70))
        }
    }

    @Bean
    fun kraftAdminRuntimeConfig() = KraftAdminRuntimeConfig()

    @Bean
    @ConditionalOnMissingBean
    fun jakartaValidationExtractor(): KraftValidationExtractor =
        JakartaValidationExtractor()

    @Bean
    fun kraftJsonSerializer(): KraftJsonSerializer = JacksonKraftJsonSerializer()

    @Bean
    @ConditionalOnMissingBean(KraftEnvironmentProvider::class)
    fun springEnvironmentProvider(): KraftEnvironmentProvider =
        SpringBootEnvironmentProvider()

    @Bean
    fun kraftAdminDescriptorFactory(
        runtimeConfig: KraftAdminRuntimeConfig,
        validationExtractor: KraftValidationExtractor,
        environmentProvider: KraftEnvironmentProvider
    ) =
        KraftAdminDescriptorFactory(
            runtimeConfig = runtimeConfig,
            validationExtractor = validationExtractor,
            environmentProvider = environmentProvider,
            entityDiscoverer = entityDiscoveryService
        )

    @Bean
    @ConditionalOnMissingBean(KraftAdminSpringbootMetaController::class)
    fun kraftAdminMetaController(
        descriptorFactory: KraftAdminDescriptorFactory,
        chain: SecurityProviderChain
    ) =
        KraftAdminSpringbootMetaController(descriptorFactory, chain, properties, applicationContext)

    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate {
        return TransactionTemplate(transactionManager)
    }

    @Bean
    @ConditionalOnMissingBean(AdminStorageProvider::class)
    fun smartStorageProvider(context: ApplicationContext): AdminStorageProvider {
        // 1. Try to find a Cloudinary bean (via reflection to avoid hard dependency)
        val cloudinary = findBeanByClassName(context, "com.cloudinary.Cloudinary")
        if (cloudinary != null) {
            logger.info("KraftAdmin: Detected Cloudinary in parent app. Using CloudinaryAdapter.")
            return CloudinaryAdapter(cloudinary)
        }

        // 2. Try to find an AWS S3 client
//        val s3 = findBeanByClassName(context, "software.amazon.awssdk.services.s3.S3Client")
//        if (s3 != null) return S3Adapter(s3)

        // 3. Fallback to local storage (or a No-Op if you prefer)
        val path = properties.storage.uploadDir
        return LocalFileSystemAdapter(path, properties.storage.publicUrlPrefix)
    }

    private fun findBeanByClassName(context: ApplicationContext, className: String): Any? {
        return try {
            val clazz = Class.forName(className)
            context.getBean(clazz)
        } catch (e: Exception) {
            null
        }
    }

}
