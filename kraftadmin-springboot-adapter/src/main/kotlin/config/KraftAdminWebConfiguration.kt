package com.kraftadmin.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import java.io.File

//@Configuration
//@AutoConfiguration
//class KraftAdminWebConfiguration(
//    private val telemetryService: KraftTelemetryService,
//    private val securityChain: SecurityProviderChain,
//    private val logService: KraftSpringLoggingService
//) : WebMvcConfigurer {
//    // These should ideally come from @ConfigurationProperties
//    private val uploadPath = "uploads/admin/"
//    private val urlPattern = "/admin/files/**"
//
//        private val logger = LoggerFactory.getLogger(javaClass)
//
//    init {
//        logger.info("KraftAdminWebConfiguration initialized")
//    }
//
//    @Bean
//    fun springGlobalBIInterceptor(): SpringKraftPulseRequestInterceptor {
//        return SpringKraftPulseRequestInterceptor(telemetryService, securityChain)
//    }
//
//    override fun addInterceptors(registry: InterceptorRegistry) {
//        registry.addInterceptor(springGlobalBIInterceptor())
//            .addPathPatterns("/**")
//            .excludePathPatterns(
//                "/static/**",
//                "/favicon.ico",
//                "/**/*.css",
//                "/**/*.js",
//                "/**/*.png",
//                "/**/*.jpg",
//                "/**/*.jpeg",
//                "/**/*.svg",
//                "/**/*.woff",
//                "/**/*.woff2",
//                "/admin/files/**" // Exclude user uploads from BI tracking
//            )
//    }
//
//    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
//        val absolutePath = File(uploadPath).absolutePath
//
//        registry.addResourceHandler("/admin/**")
//            .addResourceLocations("classpath:/META-INF/resources/admin/")
//
//        // Spring MVC to serve
//        // files from the local disk when /admin/files/ is requested.
//        registry.addResourceHandler(urlPattern)
//            .addResourceLocations("file:$absolutePath/")
//            .setCachePeriod(3600)
//        println("KraftAdmin: Serving local uploads from $absolutePath")
//    }
//
//    override fun addViewControllers(registry: ViewControllerRegistry) {
//        // Redirect /admin to /admin/index.html
//        registry.addRedirectViewController("/admin", "/admin/")
//        registry.addViewController("/admin").setViewName("forward:/admin/index.html")
//        registry.addViewController("/admin/").setViewName("forward:/admin/index.html")
//    }
//
//}

@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true")
class KraftAdminWebConfiguration : WebMvcConfigurer {

    private val uploadPath = "uploads/admin/"
    private val urlPattern = "/admin/files/**"

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolutePath = File(uploadPath).absolutePath

        // Serve the Svelte/Vue/React Admin Frontend
        registry.addResourceHandler("/admin/**")
            .addResourceLocations("classpath:/META-INF/resources/admin/")

        // Serve Uploaded Files
        registry.addResourceHandler(urlPattern)
            .addResourceLocations("file:$absolutePath/")
            .setCachePeriod(3600)
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addRedirectViewController("/admin", "/admin/")
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html")
    }

}
