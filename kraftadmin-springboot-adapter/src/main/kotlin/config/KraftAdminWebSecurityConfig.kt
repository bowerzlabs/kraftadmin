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
val basePath = properties.basePath.removeSuffix("/").ifEmpty { "/admin" }

log.info("Registering independent KraftAdmin security filter chain for {}/**", basePath)

http {
securityMatcher(AntPathRequestMatcher("$basePath/**"))
csrf { disable() }
// No server-managed HTTP session for this chain — KraftAdmin's
// own cookie (KRAFTADMIN_SESSION, via AdminSessionStore) is a
// custom-format token read directly by AdminSecurityFilter,
// not a Spring Security session.
sessionManagement { sessionCreationPolicy = org.springframework.security.config.http.SessionCreationPolicy.STATELESS }
authorizeHttpRequests {
// Permit everything at the Spring Security layer. Actual
// authentication/authorization for /admin/** happens in
// AdminSecurityFilter, registered as a plain servlet
// filter (see KraftAdminSpringSecurityConfig), which now
// runs unimpeded since nothing above it rejects the
// request first.
authorize(AntPathRequestMatcher("/**"), permitAll)
}
// No formLogin, no httpBasic, no logout handler — none of
// Spring Security's own auth mechanisms apply to this chain.
// AdminSecurityFilter is the sole authority here.
formLogin { disable() }
httpBasic { disable() }
logout { disable() }
}

return http.build()
}
}