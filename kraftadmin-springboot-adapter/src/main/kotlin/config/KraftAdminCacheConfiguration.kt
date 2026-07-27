package config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@EnableCaching
@ConditionalOnClass(CaffeineCacheManager::class)
@ConditionalOnProperty(
    prefix = "kraftadmin",
    name = ["enabled"],
    havingValue = "true"
)
class KraftAdminCacheConfiguration {

    @Bean("kraftAdminCacheManager")
    fun kraftAdminCacheManager(): CacheManager {
        return CaffeineCacheManager(
            "kraftAdminDashboard"
        ).apply {
            setCaffeine(
                Caffeine.newBuilder()
                    .maximumSize(100)
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .recordStats()
            )
        }
    }
}