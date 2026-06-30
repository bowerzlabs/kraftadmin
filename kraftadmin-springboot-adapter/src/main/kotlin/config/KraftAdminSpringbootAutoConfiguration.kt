package com.kraftadmin.config

import com.kraftadmin.KraftAdmin
import com.kraftadmin.controller.KraftAdminSpringbootMetaController
import com.kraftadmin.discovery.EntityDiscoveryService
import com.kraftadmin.discovery.ResourceGenerator
import com.kraftadmin.discovery.SpringBootEnvironmentProvider
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.utils.files.CloudinaryProvider
import com.kraftadmin.utils.files.LocalFileSystemAdapter
import com.kraftadmin.utils.files.S3Adapter
import com.kraftadmin.utils.validation.KraftValidationExtractor
import config.KraftPulseSpringKraftAdminProperties
import config.KraftPulseVersionGuardAutoConfiguration
import json.KraftJsonSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import security.SecurityProviderChain
import util.JacksonKraftJsonSerializer
import util.JakartaValidationExtractor

@AutoConfiguration
@AutoConfigureAfter(
    name = [
        "config.KraftTelemetryAutoConfiguration",
        "config.KraftSpringAuditAutoConfiguration"
    ]
)
@Import(
    KraftPulseVersionGuardAutoConfiguration::class,
    KraftAdminJpaAutoConfiguration::class,
    KraftAdminMongoAutoConfiguration::class,
    KraftAdminDiscoveryAutoConfiguration::class,
    KraftAdminWebConfiguration::class,
)
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftAdminSpringBootAutoConfiguration(
    private val properties: KraftPulseSpringKraftAdminProperties,
    private val applicationContext: ApplicationContext,
    private val entityDiscoveryService: EntityDiscoveryService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.info("KraftAdmin: Spring Boot Auto-Configuration initialized with properties: $properties")
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
                logger.info("Discovered Entity: ${it.name}")
            }

            if (entities.isEmpty()) {
                logger.warn("No entities discovered!")
                logger.warn("   Check:")
                logger.warn("   - Are your entities annotated with @Entity?")
                logger.warn("   - Is JPA/MongoDB properly configured?")
                logger.warn("   - Are entities in scanned packages?")
            }

            val resources = entities.map { ResourceGenerator.generate(it, context = context, properties = properties) }

            val config = KraftAdminConfig(
                basePath = properties.basePath,
                title = properties.title,
                discoveredEntities = entities,
                generatedResources = resources
            )

            runtimeConfig.set(config)
            KraftAdmin.start(config)

            logger.info("=" .repeat(70))
            logger.info("KraftAdmin Started Successfully")
            logger.info("   Total registered management entities: ${entities.size}")
            logger.info("=" .repeat(70))
        }
    }

    @Bean
    fun kraftAdminRuntimeConfig() = KraftAdminRuntimeConfig()

    @Bean
    @ConditionalOnMissingBean
    fun jakartaValidationExtractor(): KraftValidationExtractor = JakartaValidationExtractor()

    @Bean
    fun kraftJsonSerializer(): KraftJsonSerializer = JacksonKraftJsonSerializer()

    @Bean
    @ConditionalOnMissingBean(KraftEnvironmentProvider::class)
    fun springEnvironmentProvider(): KraftEnvironmentProvider = SpringBootEnvironmentProvider()

    @Bean
    fun kraftAdminDescriptorFactory(
        runtimeConfig: KraftAdminRuntimeConfig,
        validationExtractor: KraftValidationExtractor,
        environmentProvider: KraftEnvironmentProvider
    ) = KraftAdminDescriptorFactory(
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
    ) = KraftAdminSpringbootMetaController(descriptorFactory, chain, properties, applicationContext)

    @Bean
    fun transactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate {
        return TransactionTemplate(transactionManager)
    }

    @Bean
    @ConditionalOnMissingBean(AdminStorageProvider::class)
    fun smartStorageProvider(
        context: ApplicationContext,
        environment: Environment
    ): AdminStorageProvider {

        // 1. Cloudinary Evaluation Block ---
        val cloudinaryUrl = environment.getProperty("CLOUDINARY_URL")
            ?: environment.getProperty("cloudinary.url")

        if (!cloudinaryUrl.isNullOrBlank()) {
            val cloudinaryBean = findBeanByClassName(context, "com.cloudinary.Cloudinary")
            if (cloudinaryBean != null) {
                logger.info("KraftAdmin: Active Cloudinary instance detected in ApplicationContext.")
                return CloudinaryProvider(cloudinaryBean)
            } else {
                try {
                    val classLoader = context.classLoader ?: Thread.currentThread().contextClassLoader ?: javaClass.classLoader
                    val cloudinaryClass = Class.forName("com.cloudinary.Cloudinary", true, classLoader)
                    val instance = cloudinaryClass.getConstructor(String::class.java).newInstance(cloudinaryUrl)
                    logger.info("KraftAdmin: Spawning separate Cloudinary runtime container from environment variables.")
                    return CloudinaryProvider(instance)
                } catch (e: Exception) {
                    logger.warn("KraftAdmin: CLOUDINARY_URL found but class initialization failed: ${e.message}")
                }
            }
        }

        // 2. AWS S3 Evaluation Block ---
        val s3Bucket = environment.getProperty("AWS_S3_BUCKET")
            ?: environment.getProperty("aws.s3.bucket")

        if (!s3Bucket.isNullOrBlank()) {
            val s3ClientBean = findBeanByClassName(context, "software.amazon.awssdk.services.s3.S3Client")
            if (s3ClientBean != null) {
                logger.info("KraftAdmin: S3Client detected on context classpath. Activating SpringS3Adapter.")
                return S3Adapter(s3ClientBean, s3Bucket)
            }
        }

        //  3. Default Local File Storage Fallback ---
        // Clean fallback using the injected properties class to honor default configuration state
        val uploadDir = properties.storage?.uploadDir ?: "uploads/admin"
        val publicPrefix = properties.storage?.publicUrlPrefix ?: "/admin/files"

        logger.info("KraftAdmin: No remote cloud services resolved. Activating local disk fallback tracking: {}", uploadDir)
        return LocalFileSystemAdapter(uploadDir, publicPrefix)
    }

    private fun findBeanByClassName(context: ApplicationContext, className: String): Any? {
        return try {
            val classLoader = context.classLoader ?: Thread.currentThread().contextClassLoader ?: javaClass.classLoader
            val targetClass = Class.forName(className, false, classLoader)
            val beanNames = context.getBeanNamesForType(targetClass)
            if (beanNames.isNotEmpty()) context.getBean(beanNames.first()) else null
        } catch (e: Exception) {
            null
        }
    }

}