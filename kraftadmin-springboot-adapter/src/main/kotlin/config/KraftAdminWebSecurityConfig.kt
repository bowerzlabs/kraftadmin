package config

import com.kraftadmin.logging.KraftAdminLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher



@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
@ConditionalOnClass(SecurityFilterChain::class)
@EnableWebSecurity
class KraftAdminWebSecurityConfig(
private val properties: KraftAdminProperties,
) {
private val log = KraftAdminLogging.logger(javaClass)

    @Bean
    @Order(1)
    fun kraftAdminFilterChain(http: HttpSecurity): SecurityFilterChain {

        val basePath = properties.basePath
            .removeSuffix("/")
            .ifEmpty { "/admin" }

        log.info("Registering KraftAdmin security chain for {}", basePath)

        http
            .securityMatcher("$basePath/**")
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests {
                it.anyRequest().permitAll()
            }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }

        return http.build()
    }

}