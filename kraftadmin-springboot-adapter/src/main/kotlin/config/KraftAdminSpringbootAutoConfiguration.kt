package config

import com.kraftadmin.KraftAdmin
import com.kraftadmin.config.KraftAdminConfig
import com.kraftadmin.config.KraftAdminRuntimeConfig
import com.kraftadmin.events.KraftEventConsumer
import com.kraftadmin.events.KraftEventPublisher
import com.kraftadmin.logging.KraftAdminLogging
import persistence.metrics.KraftMetricService
import com.kraftadmin.spi.EntityDiscoveryService
import discovery.ResourceGenerator
import discovery.discoverer.environment.SpringBootEnvironmentProvider
import com.kraftadmin.spi.KraftEnvironmentProvider
import com.kraftadmin.spi.KraftMetricProvider
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.utils.files.CloudinaryProvider
import com.kraftadmin.utils.files.LocalFileSystemAdapter
import com.kraftadmin.utils.files.S3Adapter
import com.kraftadmin.utils.validation.KraftValidationExtractor
import controller.KraftAdminSpringbootUploadController
import events.KraftAdminEventLogger
import events.KraftAdminEventStore
import events.SpringKraftEventPublisher
import events.SpringActionRegistry
import events.SpringListenerRegistry
import exception.KraftAdminExceptionHandler
import json.KraftJsonSerializer
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import events.SpringKraftLifecycleService
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import persistence.jpa.bulk_actions.BulkActionService
import persistence.jpa.metrics.JpaMetricProvider
import util.JacksonKraftJsonSerializer
import validation.JakartaValidationExtractor
import java.util.concurrent.Executor
import javax.sql.DataSource

@AutoConfiguration
@Import(
    KraftAdminVersionGuardAutoConfiguration::class,
    KraftAdminJpaAutoConfiguration::class,
    KraftAdminMongoAutoConfiguration::class,
    KraftAdminDiscoveryAutoConfiguration::class,
    KraftAdminWebConfiguration::class,
    PersistenceValidatorConfiguration::class,
    KraftAdminEventLogger::class,
    KraftAdminAsyncConfiguration::class,
    KraftAdminBulkActionAutoConfiguration::class,
)
@EnableConfigurationProperties(KraftAdminProperties::class)
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftAdminSpringBootAutoConfiguration(
    private val properties: KraftAdminProperties,
    private val applicationContext: ApplicationContext
) {

    private val logger = KraftAdminLogging.logger(javaClass)

    init {
        KraftAdminLogging.enabled =
            properties.loggingConfig.enabled
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
                logger.info("Discovered Entity: ${it.entityClass.name}")
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
        }
    }

    @Bean
    @ConditionalOnMissingBean(SpringActionRegistry::class)
    fun springActionRegistry(applicationContext: ApplicationContext): SpringActionRegistry {
        return SpringActionRegistry(applicationContext)
    }

    @Bean
    @ConditionalOnMissingBean(SpringListenerRegistry::class)
    fun springListenerRegistry(applicationContext: ApplicationContext): SpringListenerRegistry {
        return SpringListenerRegistry(applicationContext)
    }

    @Bean
    @ConditionalOnMissingBean(KraftEventPublisher::class)
    fun kraftEventPublisher(
        registry: SpringListenerRegistry,
        consumers: List<KraftEventConsumer>,
        @Qualifier("kraftEventExecutor") kraftEventExecutor: Executor
    ): KraftEventPublisher {
        return SpringKraftEventPublisher(registry, consumers, kraftEventExecutor = kraftEventExecutor)
    }

    @Bean
    @ConditionalOnMissingBean(SpringKraftLifecycleService::class)
    fun kraftSpringLifeCycle(publisher: KraftEventPublisher): SpringKraftLifecycleService {
        return SpringKraftLifecycleService(publisher)
    }

    @Bean
    @ConditionalOnMissingBean
    fun kraftEventStore() : KraftAdminEventStore {
        return KraftAdminEventStore()
    }

    @Bean
    @ConditionalOnMissingBean(KraftMetricProvider::class)
    fun kraftMetricsProvider(
        entityManager: EntityManager,
        transactionTemplate: TransactionTemplate
    ): KraftMetricProvider {
        return JpaMetricProvider(
            entityManager = entityManager,
            transactionTemplate = transactionTemplate
        )
    }


    @Bean
    @ConditionalOnMissingBean(KraftMetricService::class)
    fun kraftMetricsService(
        metricProviders: List<KraftMetricProvider>
    ): KraftMetricService {
        return KraftMetricService(metricProviders)
    }

    @Bean
    @ConditionalOnMissingBean
    fun kraftAdminExceptionHandler() = KraftAdminExceptionHandler()


    @Bean
    fun kraftAdminRuntimeConfig() = KraftAdminRuntimeConfig()

    @Bean
    @ConditionalOnMissingBean
    fun jakartaValidationExtractor(): KraftValidationExtractor = JakartaValidationExtractor()

    @Bean
    fun kraftJsonSerializer(): KraftJsonSerializer = JacksonKraftJsonSerializer()

    @Bean
    @ConditionalOnMissingBean(KraftEnvironmentProvider::class)
    fun springBootEnvironmentProvider(
        environment: Environment,
        dataSources: ObjectProvider<DataSource>,
    ): KraftEnvironmentProvider =
        SpringBootEnvironmentProvider(
            environment = environment,
            dataSources = dataSources,
            applicationContext,
            appVersion = properties.version
        )


    @Bean
    fun kraftAdminDescriptorFactory(
        runtimeConfig: KraftAdminRuntimeConfig,
        validationExtractor: KraftValidationExtractor,
        environmentProvider: KraftEnvironmentProvider,
        entityDiscoveryService: EntityDiscoveryService,
    ): KraftAdminDescriptorFactory {
        return KraftAdminDescriptorFactory(
            runtimeConfig = runtimeConfig,
            validationExtractor = validationExtractor,
            environmentProvider = environmentProvider,
            entityDiscoverer = entityDiscoveryService
        )
    }

    @Bean
    @ConditionalOnMissingBean(KraftAdminSpringbootUploadController::class)
    fun kraftSprinbootUploadController(adminStorageProvider: AdminStorageProvider) =
        KraftAdminSpringbootUploadController(adminStorageProvider)

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