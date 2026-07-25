package config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import util.KraftRequestTimingInterceptor

import java.io.File

@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftAdminWebConfiguration : WebMvcConfigurer {

    private val uploadPath = "uploads/admin/"
    private val urlPattern = "/admin/files/**"

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolutePath = File(uploadPath).absolutePath

        // Serve the Svelte Admin Frontend
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

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(KraftRequestTimingInterceptor())
            .addPathPatterns("/admin/api/**")
    }

}
