package com.kraftadmin.config

import com.kraftadmin.controller.KraftAdminSpringbootLogController
import com.kraftadmin.util.KraftSpringLoggingService
import com.kraftadmin.util.SpringGlobalBIInterceptor
import com.kraftadmin.utils.telementary.KraftTelemetryService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import org.springframework.web.servlet.resource.ResourceResolverChain
import security.AdminSecurityProvider
import security.SecurityProviderChain
import java.io.File

//@Configuration
@AutoConfiguration
class KraftAdminWebConfiguration(
    private val telemetryService: KraftTelemetryService,
    private val securityChain: SecurityProviderChain,
    private val logService: KraftSpringLoggingService
) : WebMvcConfigurer {
    // These should ideally come from @ConfigurationProperties
    private val uploadPath = "uploads/admin/"
    private val urlPattern = "/admin/files/**"

        private val logger = LoggerFactory.getLogger(javaClass)

    init {
        logger.info("KraftAdminWebConfiguration initialized")
    }

    @Bean
    fun springGlobalBIInterceptor(): SpringGlobalBIInterceptor {
        return SpringGlobalBIInterceptor(telemetryService, securityChain)
    }

    @Bean
    fun kraftLogController() = KraftAdminSpringbootLogController(logService)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(springGlobalBIInterceptor())
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/static/**",
                "/favicon.ico",
                "/**/*.css",
                "/**/*.js",
                "/**/*.png",
                "/**/*.jpg",
                "/**/*.jpeg",
                "/**/*.svg",
                "/**/*.woff",
                "/**/*.woff2",
                "/admin/files/**" // Exclude user uploads from BI tracking
            )
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolutePath = File(uploadPath).absolutePath

        registry.addResourceHandler("/admin/**")
            .addResourceLocations("classpath:/META-INF/resources/admin/")

        // Spring MVC to serve
        // files from the local disk when /admin/files/ is requested.
        registry.addResourceHandler(urlPattern)
            .addResourceLocations("file:$absolutePath/")
            .setCachePeriod(3600)
        println("KraftAdmin: Serving local uploads from $absolutePath")
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Redirect /admin to /admin/index.html
        registry.addRedirectViewController("/admin", "/admin/")
        registry.addViewController("/admin").setViewName("forward:/admin/index.html")
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html")
    }

}
