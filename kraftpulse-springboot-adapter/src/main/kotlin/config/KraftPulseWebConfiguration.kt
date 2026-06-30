package config

import interceptor.PulseTelemetryCaptor
import interceptor.SpringKraftPulseRequestInterceptor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import security.SecurityProviderChain
import telemetry.KraftTelemetryService
import util.KraftSpringLoggingService

@AutoConfiguration
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
@ConditionalOnClass(name = ["org.springframework.boot.web.servlet.error.ErrorAttributes"])
class KraftPulseWebConfiguration(
    private val telemetryService: KraftTelemetryService,
    private val securityChain: SecurityProviderChain,
    private val logService: KraftSpringLoggingService
) : WebMvcConfigurer {

    @Bean
    fun pulseTelemetryCaptor() =
        PulseTelemetryCaptor(telemetryService, securityChain)

    @Bean
    fun springKraftPulseRequestInterceptor(captor: PulseTelemetryCaptor) =
        SpringKraftPulseRequestInterceptor(captor)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(springKraftPulseRequestInterceptor(pulseTelemetryCaptor()))
            .addPathPatterns("/**")
            .excludePathPatterns(
                // Static assets
                "/static/**",
                "/favicon.ico",
                "/**/*.css",
                "/**/*.js",
                "/**/*.png",
                "/**/*.jpg",
                "/**/*.svg",
                "/admin/files/**",
                // KraftAdmin's own routes — never track internal traffic
                "/kraft-admin/**",
                "/kraft-pulse/**"
            )
    }

}