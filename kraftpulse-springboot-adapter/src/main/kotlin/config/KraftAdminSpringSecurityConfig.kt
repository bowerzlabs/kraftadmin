package config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import security.AdminSecurityConfig
import security.AdminSecurityFilter
import security.AdminSecurityProvider
import security.AdminSessionStore
import security.BuiltinBasicAuthProvider
import security.SecurityProviderChain
import security.SessionConfig
import security.SessionSecurityProvider
import security.SpringSecurityAdapter

@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true")
class KraftAdminSpringSecurityConfig(
    private val properties: KraftPulseSpringKraftAdminProperties,
    private val env: org.springframework.core.env.Environment,
) {

    private val log = LoggerFactory.getLogger(KraftAdminSpringSecurityConfig::class.java)

    @Bean
    @ConditionalOnMissingBean
    fun adminSecurityConfig(): AdminSecurityConfig =
        AdminSecurityConfig(
            frameworkAdapterFactory = { SpringSecurityAdapter() },
            frameworkSecurityActiveCheck = { isSpringSecurityActive() },
        )

    @Bean
    fun adminSessionStore(config: AdminSecurityConfig): AdminSessionStore =
        AdminSessionStore(config.sessionConfig)

    @Bean
    fun sessionConfig(): SessionConfig = SessionConfig()


    @Bean
    fun builtinBasicAuthProvider(): BuiltinBasicAuthProvider {
        val basicAuthConfig = properties.security.basicAuth
        return BuiltinBasicAuthProvider(basicAuthConfig)
    }

    /**
     * The unified security chain used by both the Filter and the AuthController.
     */
    @Bean
    fun securityProviderChain(
        config: AdminSecurityConfig,
        sessionStore: AdminSessionStore,
        builtinProvider: BuiltinBasicAuthProvider
    ): SecurityProviderChain {
        val providers = mutableListOf<AdminSecurityProvider>()

        if (isSpringSecurityActive()) {
            // ONLY use the adapter. Do NOT add the builtin provider.
            // This stops the library from trying to manage its own "admin" user.
            providers.add(SpringSecurityAdapter())
        } else {
            // No parent security? Use our standalone session + basic auth.
            providers.add(SessionSecurityProvider(sessionStore))
            providers.add(builtinProvider)
        }

        return SecurityProviderChain(providers.sortedBy { it.priority })
    }

//    @Bean
//    fun adminSecurityFilter(
//        chain: SecurityProviderChain
//    ): FilterRegistrationBean<AdminSecurityFilter> {
//        val filter = AdminSecurityFilter(
//            chain,
//            securityConfig = AdminSecurityConfig(),
//        )
//        return FilterRegistrationBean(filter).apply {
//            addUrlPatterns("/admin/*")
////            order = Ordered.HIGHEST_PRECEDENCE + 10
//            // Spring Security usually starts at -100.
//            // By setting this to -90, we ensure Spring Security has already
//            // identified the user (briannyadero443@gmail.com) before we check the context.
//            order = -90;
//        }
//    }

    @Bean
    fun adminSecurityFilter(
        chain: SecurityProviderChain
    ): FilterRegistrationBean<AdminSecurityFilter> {
        val registration = FilterRegistrationBean(AdminSecurityFilter(chain))
        registration.addUrlPatterns("/admin/*")

        // Use a positive number to ensure we are well outside
        // the Spring Security internal filter chain range.
        registration.order = 100
        return registration
    }


//    companion object {
//        @JvmStatic
//        fun isSpringSecurityActive(): Boolean = try {
//            Class.forName("org.springframework.security.web.SecurityFilterChain", false, javaClass.classLoader)
//            true
//        } catch (_: ClassNotFoundException) {
//            false
//        }
//    }

    companion object {
        @JvmStatic
        fun isSpringSecurityActive(): Boolean = try {
            Class.forName(
                "org.springframework.security.web.SecurityFilterChain",
                false,
                KraftAdminSpringSecurityConfig::class.java.classLoader
            )
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}