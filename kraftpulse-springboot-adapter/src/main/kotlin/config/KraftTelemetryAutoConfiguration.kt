package config

import analytics.AnalyticsReader
import analytics.CloudAnalyticsProvider
import analytics.LocalAnalyticsProvider
import analytics.TelemetryWriter
import json.KraftJsonSerializer
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.client.RestTemplate
import telemetry.KraftTelemetryService
import telemetry.NoOpTelemetryService
import telemetry.SQLiteTelemetryProvider
import util.JacksonKraftJsonSerializer
import util.SpringBootTelemetryService
import java.util.concurrent.Executor

@Configuration
class KraftTelemetryAutoConfiguration(
    private val environment: Environment,
    private val properties: KraftPulseSpringKraftAdminProperties
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
    )
    fun telemetryRestTemplate(): RestTemplate {
        val restTemplate = RestTemplate()
        val config = properties.telemetryConfig

        // Automatically inject the API Key and Secret Key headers into every outgoing sync request
        restTemplate.interceptors.add(ClientHttpRequestInterceptor { request, body, execution ->
            request.headers.add("Content-Type", "application/json")
            request.headers.add("Accept", "application/json")
            config.apiKey?.let { request.headers.add("X-Pulse-API-Key", it) }
            config.secretKey?.let { request.headers.add("X-Pulse-Secret-Key", it) }
            execution.execute(request, body)
        })

        return restTemplate
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
    )
    fun sqliteTelemetryProvider(
        publisher: ApplicationEventPublisher
    ): SQLiteTelemetryProvider {
        val path = properties.telemetryConfig.path ?: ".kraft-telemetry.db"
        val provider = SQLiteTelemetryProvider(
            appName = environment.getProperty("spring.application.name") ?: "KraftPulse",
            serializer = kraftPulseJsonSerializer()
        )
        provider.onEventPersisted = { event -> publisher.publishEvent(event) }
        return provider
    }

    // Always available — this is the durable write path regardless of provider
    @Bean
    fun localAnalyticsProvider(sqLiteTelemetryProvider: SQLiteTelemetryProvider): LocalAnalyticsProvider {
        return LocalAnalyticsProvider(sqLiteTelemetryProvider)
    }

    @Bean
    fun telemetryWriter(local: LocalAnalyticsProvider): TelemetryWriter = local

    // Reader switches based on config — local SQLite or cloud ClickHouse-backed API
    @Bean
    @Primary
    fun analyticsReader(
        local: LocalAnalyticsProvider,
        kraftJsonSerializer: KraftJsonSerializer
    ): AnalyticsReader {
        return when (properties.telemetryConfig.provider) {
            TelemetryProvider.LOCAL -> local
            TelemetryProvider.CLOUD -> CloudAnalyticsProvider(
                apiKey = properties.telemetryConfig.apiKey ?: "",
                secretKey = properties.telemetryConfig.secretKey ?: "",
                serializer = kraftJsonSerializer,
                baseUrl = properties.telemetryConfig.cloudUrl ?: "http://localhost:8090"
            )
        }
    }

    @Bean
    @Primary
    @ConditionalOnExpression("\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}")
    fun telemetryService(
        kraftJsonSerializer: KraftJsonSerializer,
        provider: SQLiteTelemetryProvider,
        analyticsReader: AnalyticsReader, // ✅ renamed — read-only, used for dashboard queries
        telemetryRestTemplate: RestTemplate,
    ): KraftTelemetryService {
        return SpringBootTelemetryService(
            properties = properties,
            serializer = kraftJsonSerializer,
            commonStore = provider,
            analyticsReader = analyticsReader, // ✅ only used for reads now
            restTemplate = telemetryRestTemplate
        )
    }

//    @Bean
//    @Primary
//    @ConditionalOnExpression(
//        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
//    )
//    fun telemetryService(
//        kraftJsonSerializer: KraftJsonSerializer,
//        provider: SQLiteTelemetryProvider,
//        analyticsProvider: AnalyticsProvider,
//        telemetryRestTemplate: RestTemplate // Inject the secure RestTemplate here
//    ): KraftTelemetryService {
//        return SpringBootTelemetryService(
//            properties = properties,
//            serializer = kraftJsonSerializer,
//            commonStore = provider,
//            analyticsProvider = analyticsProvider,
//            restTemplate = telemetryRestTemplate // Pass it along
//        )
//    }

    @Bean(name = ["kraftTelemetryExecutor"])
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
    )
    fun kraftTelemetryExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 500
        executor.setThreadNamePrefix("KraftTelemetry-")
        executor.initialize()
        return executor
    }

    @Bean
    @ConditionalOnMissingBean(KraftTelemetryService::class)
    fun noOpTelemetryService(): KraftTelemetryService = NoOpTelemetryService()

    @Bean
    @Primary
    fun kraftPulseJsonSerializer(): KraftJsonSerializer = JacksonKraftJsonSerializer()

    @Bean
    @ConditionalOnProperty(prefix = "kraftpulse.telemetry-config", name = ["enabled"], havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(AnalyticsReader::class)
    fun analyticsProvider(sqliteProvider: SQLiteTelemetryProvider?): AnalyticsReader {
        val config = properties.telemetryConfig
        val providerType = config.provider ?: TelemetryProvider.LOCAL

        return when (providerType) {
            TelemetryProvider.CLOUD -> {
                val apiKey = config.apiKey ?: throw IllegalStateException("Missing apiKey")
                val secretKey = config.secretKey ?: throw IllegalStateException("Missing secretKey")
                val targetBaseUrl = config.cloudUrl ?: "http://localhost:8090"

                CloudAnalyticsProvider(
                    apiKey = apiKey,
                    secretKey = secretKey,
                    serializer = kraftPulseJsonSerializer(),
                    baseUrl = targetBaseUrl.removeSuffix("/")
                )
            }
            TelemetryProvider.LOCAL -> {
                if (sqliteProvider == null) throw IllegalStateException("Missing SQLiteTelemetryProvider")
                LocalAnalyticsProvider(sqliteProvider)
            }
        }
    }
}