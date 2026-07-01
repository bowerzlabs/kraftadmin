package config

import analytics.AnalyticsReader
import analytics.CloudAnalyticsProvider
import analytics.LocalAnalyticsProvider
import analytics.TelemetryWriter
import interceptor.PulseTelemetryCaptor
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
import security.SecurityProviderChain
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
            serializer = kraftPulseJsonSerializer(),
            enabled = true
        )
        provider.onEventPersisted = { event -> publisher.publishEvent(event) }
        return provider
    }

    // Always available — this is the durable write path regardless of provider
    @Bean
    @ConditionalOnMissingBean(name = ["telemetryWriter"])
    fun localAnalyticsProvider(sqLiteTelemetryProvider: SQLiteTelemetryProvider): LocalAnalyticsProvider {
        return LocalAnalyticsProvider(sqLiteTelemetryProvider)
    }

    @Bean
    @Primary
    fun telemetryWriter(local: LocalAnalyticsProvider): TelemetryWriter = local


    @Bean
    @Primary
    @ConditionalOnExpression("\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}")
    fun telemetryService(
        kraftJsonSerializer: KraftJsonSerializer,
        provider: SQLiteTelemetryProvider,
        analyticsReader: AnalyticsReader,
        telemetryRestTemplate: RestTemplate
    ): KraftTelemetryService {
        return SpringBootTelemetryService(
            properties = properties,
            serializer = kraftJsonSerializer,
            commonStore = provider,
            analyticsReader = analyticsReader,
            restTemplate = telemetryRestTemplate
        )
    }

//    @Bean
//    @Primary
//    @ConditionalOnExpression("\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}")
//    fun telemetryService(
//        kraftJsonSerializer: KraftJsonSerializer,
//        provider: SQLiteTelemetryProvider,
//        analyticsReader: AnalyticsReader, // ✅ renamed — read-only, used for dashboard queries
//        telemetryRestTemplate: RestTemplate,
//    ): KraftTelemetryService {
//        return SpringBootTelemetryService(
//            properties = properties,
//            serializer = kraftJsonSerializer,
//            commonStore = provider,
//            analyticsReader = analyticsReader, // ✅ only used for reads now
//            restTemplate = telemetryRestTemplate
//        )
//    }

    @Bean
    @ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true")
    fun kraftScheduledTaskAspect(captor: PulseTelemetryCaptor): KraftScheduledTaskAspect {
        return KraftScheduledTaskAspect(captor)
    }

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

    /**
     * This is the single source of truth for the AnalyticsReader.
     * It handles the switch between LOCAL and CLOUD automatically.
     */
    @Bean
    @ConditionalOnProperty(prefix = "kraftpulse.telemetry-config", name = ["enabled"], havingValue = "true", matchIfMissing = false)
    @ConditionalOnMissingBean(AnalyticsReader::class)
    fun analyticsReader(sqliteProvider: SQLiteTelemetryProvider?): AnalyticsReader {
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

//A013222343B