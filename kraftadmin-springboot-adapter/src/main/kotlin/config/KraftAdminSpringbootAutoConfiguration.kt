package com.kraftadmin.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.kraftadmin.KraftAdmin
import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.controller.KraftAdminSpringbootMetaController
import com.kraftadmin.discovery.EntityDiscoveryService
import com.kraftadmin.discovery.ResourceGenerator
import com.kraftadmin.discovery.SpringBootEnvironmentProvider
import com.kraftadmin.enums.KraftLogAction
import com.kraftadmin.persistence.service.KraftSettingsService
import com.kraftadmin.security.AdminPrincipal
import com.kraftadmin.utils.logging.KraftAdminAuditor
import security.SecurityProviderChain
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.util.JakartaValidationExtractor
import com.kraftadmin.util.KraftSpringLoggingAuditor
import com.kraftadmin.util.KraftSpringLoggingService
import com.kraftadmin.util.SpringBootTelemetryService
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.utils.files.CloudinaryAdapter
import com.kraftadmin.utils.files.LocalFileSystemAdapter
import com.kraftadmin.utils.telementary.KraftTelemetryService
import com.kraftadmin.utils.validation.KraftValidationExtractor
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.Executor


@AutoConfiguration
@Import(
    KraftAdminJpaAutoConfiguration::class,
    KraftAdminMongoAutoConfiguration::class,
    KraftAdminDiscoveryAutoConfiguration::class,
    KraftAdminWebConfiguration::class,
    KraftAdminSpringSecurityConfig::class,
)
@EnableConfigurationProperties(SpringKraftAdminProperties::class)
class KraftAdminSpringBootAutoConfiguration(
    private val properties: SpringKraftAdminProperties,
    private val applicationContext: ApplicationContext,
    private val entityDiscoveryService: EntityDiscoveryService
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun kraftAdminRunner(
        discoveryService: EntityDiscoveryService,
        runtimeConfig: KraftAdminRuntimeConfig,
        context: ApplicationContext
    ): ApplicationRunner {
        return ApplicationRunner {
            logger.info("=" .repeat(70))
            logger.info("KraftAdmin - Initializing")
            logger.info("=" .repeat(70))

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
            logger.info("Generated resources:")
            resources.forEach { res ->
                logger.info("   • ${res.name} (${res.entityClass.simpleName}) ${res.columns}")
            }

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

//    @Bean
//    fun hibernateModule(): Hibernate6Module {
//        val module = Hibernate6Module()
//        // Disable the features that force Jackson to look at proxies
//        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION)
//        return module
//    }

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
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            // This module tells Jackson: "If you see a Hibernate Proxy, ignore the interceptors"
            .registerModule(com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Bean
    @ConditionalOnMissingBean(KraftAdminAuditor::class)
    fun defaultAdminAuditor(): KraftAdminAuditor {
        val logger = LoggerFactory.getLogger("KRAFT_ADMIN_AUDIT")

        return object : KraftAdminAuditor {
//            override fun record(action: KraftLogAction, resource: String, id: String, actor: AdminUserDTO) {
//                // This format is "Log-Parser Friendly" (Grok/ELK)
//                logger.info("ACTION={} RESOURCE={} ID={} ACTOR={}", action, resource, id, actor)
//            }

            override fun record(
                action: KraftLogAction,
                resource: String,
                id: String,
                actor: AdminUserDTO
            ) {
                logger.info("ACTION={} RESOURCE={} ID={} ACTOR={}", action, resource, id, actor)

            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(KraftTelemetryService::class)
    fun telemetryService(): KraftTelemetryService {
        return SpringBootTelemetryService(properties = properties)
    }

    @Bean(name = ["kraftTelemetryExecutor"])
    fun kraftTelemetryExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.setQueueCapacity(500)
        executor.setThreadNamePrefix("KraftTelemetry-")
        executor.initialize()
        return executor
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

    @Bean
    @ConditionalOnMissingBean
    fun kraftLoggingService(): KraftSpringLoggingService = KraftSpringLoggingService()

    @Bean
//    @ConditionalOnMissingBean
    @Primary // Force this to be the winner if multiple exist
    fun kraftAuditor(service: KraftSpringLoggingService): KraftAdminAuditor =
        KraftSpringLoggingAuditor(service)
//
//    @Bean
//    @ConditionalOnMissingBean
//    fun kraftSettingsService(
//        properties: SpringKraftAdminProperties,
//        objectMapper: ObjectMapper
//    ) = KraftSettingsService(properties, objectMapper, "kraft-settings.json")

    private fun findBeanByClassName(context: ApplicationContext, className: String): Any? {
        return try {
            val clazz = Class.forName(className)
            context.getBean(clazz)
        } catch (e: Exception) {
            null
        }
    }

}
