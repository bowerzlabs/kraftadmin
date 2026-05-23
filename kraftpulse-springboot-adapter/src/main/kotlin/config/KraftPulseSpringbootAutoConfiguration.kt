//package config
//
//import interceptor.KraftHttpClientInterceptor
//import interceptor.PulseTelemetryCaptor
//import jakarta.annotation.PreDestroy
//import org.springframework.beans.factory.config.BeanPostProcessor
//import org.springframework.boot.ApplicationRunner
//import org.springframework.boot.autoconfigure.AutoConfiguration
//import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
//import org.springframework.boot.context.properties.EnableConfigurationProperties
//import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer
//import org.springframework.boot.web.servlet.error.ErrorAttributes
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Import
//import org.springframework.web.client.RestTemplate
//import telementary.KraftTelemetryService
//import telemetry.KraftPulse
//
//@AutoConfiguration
//@Import(
//    KraftTelemetryAutoConfiguration::class,
//    KraftPulseWebConfiguration::class,
//    KraftAnalyticsConfiguration::class,
//    KraftSpringAuditAutoConfiguration::class,
//    KraftAdminSpringSecurityConfig::class,
//    JpaPulseAutoconfiguration::class,
//    KraftScheduledTaskAspect::class
//)
//@EnableConfigurationProperties(KraftPulseSpringKraftAdminProperties::class)
//@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
//class KraftPulseSpringbootAutoConfiguration {
//
//    @Bean
//    @ConditionalOnExpression(
//        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
//    )
//    fun kraftPulseStarter(): ApplicationRunner {
//        return ApplicationRunner {
//            // This links your static object to the Spring lifecycle
//            KraftPulse.start()
//        }
//    }
//
//    @Bean
//    @ConditionalOnMissingBean(ErrorAttributes::class)
//    fun kraftPulseErrorAttributes(sink: KraftTelemetryService): ErrorAttributes {
//        return KraftPulseErrorAttributes(sink)
//    }
//
//    @Bean
//    fun kraftRestTemplateBeanPostProcessor(captor: PulseTelemetryCaptor): BeanPostProcessor {
//        return object : org.springframework.beans.factory.config.BeanPostProcessor {
//            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
//                if (bean is RestTemplate) {
//                    // Prepend our custom metrics interceptor to the template's pipeline execution lists
//                    val interceptors = bean.interceptors
//                    interceptors.add(KraftHttpClientInterceptor(captor))
//                    bean.interceptors = interceptors
//                }
//                return bean
//            }
//        }
//    }
//
//    @Bean
//    fun kraftPulseStarter(captor: PulseTelemetryCaptor): ApplicationRunner {
//        return ApplicationRunner {
//            // Enforce global factory tracking hook mechanisms
//            try {
//                java.net.URL.setURLStreamHandlerFactory { protocol ->
//                    if (protocol == "http" || protocol == "https") {
//                        // Instantiates and maps your tracked protocol stream delegates
//                        // intercepting everything system-wide
//                    }
//                    null
//                }
//                println("🚀 KraftPulse: System-wide low level HTTP tracing active.")
//            } catch (e: Error) {
//                // Fails gracefully if another agent has locked the factory pool state
//                println("⚠️ KraftPulse: Global stream factory locked. Falling back to BeanPostProcessor interception.")
//            }
//            KraftPulse.start()
//        }
//    }
//
//    @PreDestroy
//    fun onShutdown() {
//        KraftPulse.stop()
//    }
//
//}

package config

import analytics.AnalyticsProvider
import controller.KraftAdminSpringbootLogController
import controller.KraftSpringAnalyticsController
import controller.KraftSpringMonitoringController
import interceptor.KraftHttpClientInterceptor
import interceptor.PulseTelemetryCaptor
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
import telementary.KraftTelemetryService
import telemetry.KraftPulse
import util.KraftSpringLoggingService

@AutoConfiguration
@Import(
    KraftTelemetryAutoConfiguration::class,
    KraftPulseWebConfiguration::class,
    KraftAnalyticsConfiguration::class,
    KraftSpringAuditAutoConfiguration::class,
    KraftAdminSpringSecurityConfig::class,
    JpaPulseAutoconfiguration::class,
    KraftScheduledTaskAspect::class
)
@EnableConfigurationProperties(KraftPulseSpringKraftAdminProperties::class)
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class KraftPulseSpringbootAutoConfiguration {

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

//    @Bean
//    fun kraftRestTemplateBeanPostProcessor(captor: PulseTelemetryCaptor): BeanPostProcessor {
//        return object : BeanPostProcessor {
//            override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
//                // Intercept and instrument container-managed RestTemplate instances exclusively
//                if (bean is RestTemplate) {
//                    val interceptors = bean.interceptors
//                    // Guard condition to prevent duplicate tracking layer injection loops
//                    if (interceptors.none { it is KraftHttpClientInterceptor }) {
//                        interceptors.add(KraftHttpClientInterceptor(captor))
//                        bean.interceptors = interceptors
//                    }
//                }
//                return bean
//            }
//        }
//    }

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
    fun kraftSpringAnalyticsController(analyticsProvider: AnalyticsProvider): KraftSpringAnalyticsController {
        return KraftSpringAnalyticsController(analyticsProvider)
    }

    @Bean
    @ConditionalOnMissingBean(KraftSpringMonitoringController::class)
    @ConditionalOnExpression(
        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
    )

    fun kraftSpringMonitoringController(
        telemetryService: KraftTelemetryService
    ): KraftSpringMonitoringController {
        return KraftSpringMonitoringController(telemetryService)
    }

    @Bean
    @ConditionalOnMissingBean(ErrorAttributes::class)
    fun kraftPulseErrorAttributes(sink: KraftTelemetryService): ErrorAttributes {
        return KraftPulseErrorAttributes(sink)
    }

    @PreDestroy
    fun onShutdown() {
        KraftPulse.stop()
    }
}

