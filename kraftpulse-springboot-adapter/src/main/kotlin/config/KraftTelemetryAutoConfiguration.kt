package config

import json.KraftJsonSerializer
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import telementary.KraftTelemetryService
import telemetry.SQLiteTelemetryProvider
import telemetry.NoOpTelemetryService
import util.JacksonKraftJsonSerializer
import util.SpringBootTelemetryService
import java.util.concurrent.Executor

@Configuration
class KraftTelemetryAutoConfiguration(
    private val environment: Environment,
    private val properties: KraftPulseSpringKraftAdminProperties
) {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
    )
    fun sqliteTelemetryProvider(
        properties: KraftPulseSpringKraftAdminProperties,
        publisher: ApplicationEventPublisher
    ): SQLiteTelemetryProvider {
        // Use the path from properties or the default hidden file
        val path = properties.telemetryConfig.path ?: ".kraft-telemetry.db"

        val provider = SQLiteTelemetryProvider(
            appName = environment.getProperty("spring.application.name") ?: "KraftPulse",
            serializer = kraftPulseJsonSerializer()
        )

        // The Bridge: SQLite Write -> Spring Event -> Controller SSE / Analytics
        provider.onEventPersisted = { event ->
            publisher.publishEvent(event)
        }

        return provider
    }
    
    @Bean
    @Primary
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
    )
    fun telemetryService(
        kraftJsonSerializer: KraftJsonSerializer,
        provider: SQLiteTelemetryProvider // Inject the managed bean here!
    ): KraftTelemetryService {
        return SpringBootTelemetryService(
            properties = properties,
            serializer = kraftJsonSerializer,
            commonStore = provider
        )
    }

    @Bean(name = ["kraftTelemetryExecutor"])
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:true} and \${kraftpulse.telemetry-config.enabled:true}"
    )
    fun kraftTelemetryExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.setQueueCapacity(500)
        executor.setThreadNamePrefix("KraftTelemetry-")
        executor.initialize()
        return executor
    }

    /**
     * The Fallback: This satisfies components like the Auditor or ErrorAttributes
     * when the real telemetryService is disabled in YAML.
     */
    @Bean
    @ConditionalOnMissingBean(KraftTelemetryService::class)
    fun noOpTelemetryService(): KraftTelemetryService = NoOpTelemetryService()

    @Bean
    @Primary
    fun kraftPulseJsonSerializer(): KraftJsonSerializer = JacksonKraftJsonSerializer()

}

// bamako or neocidil 600ew


