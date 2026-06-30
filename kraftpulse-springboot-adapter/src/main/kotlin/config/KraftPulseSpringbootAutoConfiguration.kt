package config

import analytics.AnalyticsReader
import controller.KraftAdminSpringbootLogController
import controller.KraftSpringAnalyticsController
import controller.KraftSpringMonitoringController
import interceptor.KraftHttpClientInterceptor
import interceptor.PulseTelemetryCaptor
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.error.ErrorAttributes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.client.RestTemplate
import telemetry.KraftTelemetryService
import telemetry.KraftPulse
import telemetry.micrometer.KraftPulseCacheManagerWrapper
import util.KraftSpringLoggingService

@AutoConfiguration
@Import(
    KraftPulseVersionGuardAutoConfiguration::class,
    KraftTelemetryAutoConfiguration::class,
    KraftPulseWebConfiguration::class,
    KraftSpringAuditAutoConfiguration::class,
    KraftAdminSpringSecurityConfig::class,
    JpaPulseAutoconfiguration::class,
    KraftScheduledTaskAspect::class,
    KraftPulseSchedulingAutoconfiguration::class
)
@EnableConfigurationProperties(KraftPulseSpringKraftAdminProperties::class)
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftPulseSpringbootAutoConfiguration(
    @field:Autowired val meterRegistry: MeterRegistry?
) {

    @Bean
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
    )
    fun kraftPulseStarter(): ApplicationRunner {
        return ApplicationRunner {
            // Fires up the core telemetry outbox background worker threads safely
            KraftPulse.start()
        }
    }

    @Bean
    fun kraftRestTemplateBeanPostProcessor(
        @Autowired(required = false) captor: PulseTelemetryCaptor?
    ): BeanPostProcessor {
        return object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                // If the captor bean wasn't created, skip intercepting completely
                if (captor == null) return bean

                if (bean is RestTemplate) {
                    val interceptors = bean.interceptors
                    if (interceptors.none { it is KraftHttpClientInterceptor }) {
                        interceptors.add(KraftHttpClientInterceptor(captor))
                        bean.interceptors = interceptors
                    }
                }
                return bean
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(KraftAdminSpringbootLogController::class)
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
    )
    fun kraftAdminSpringbootLogController(
        loggingService: KraftSpringLoggingService
    ): KraftAdminSpringbootLogController {
        return KraftAdminSpringbootLogController(loggingService)
    }

    @Bean
    @ConditionalOnMissingBean(KraftSpringAnalyticsController::class)
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
    )
    fun kraftSpringAnalyticsController(analyticsReader: AnalyticsReader): KraftSpringAnalyticsController {
        return KraftSpringAnalyticsController(analyticsReader)
    }

    @Bean
    @ConditionalOnMissingBean(KraftSpringMonitoringController::class)
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
    )

    fun kraftSpringMonitoringController(
        telemetryService: AnalyticsReader
    ): KraftSpringMonitoringController {
        return KraftSpringMonitoringController(telemetryService )
    }

    @Bean
    @ConditionalOnMissingBean(ErrorAttributes::class)
    fun kraftPulseErrorAttributes(sink: KraftTelemetryService): ErrorAttributes {
        return KraftPulseErrorAttributes(sink)
    }

    @Bean
    fun kraftCacheManagerBeanPostProcessor(): BeanPostProcessor {
        return object : BeanPostProcessor {
            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
                return try {
                    val cacheManagerClass = Class.forName("org.springframework.cache.CacheManager")
                    if (cacheManagerClass.isInstance(bean)) {
                        println("KraftPulse: Instrumenting CacheManager [$beanName]")
                        KraftPulseCacheManagerWrapper.wrap(bean)
                    } else bean
                } catch (e: ClassNotFoundException) {
                    bean // spring-context-support not on classpath — no-op
                }
            }
        }
    }

    @PreDestroy
    fun onShutdown() {
        KraftPulse.stop()
    }


}

