package config

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.security.AdminSecurityConfig
import com.kraftadmin.security.AdminSecurityProvider
import com.kraftadmin.security.AdminSessionStore
import com.kraftadmin.security.BuiltinBasicAuthProvider
import com.kraftadmin.security.DefaultSessionConfig
import com.kraftadmin.security.SessionConfig
import com.kraftadmin.security.SessionSecurityProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import security.AdminSecurityFilter
import security.SecurityProviderChain
import security.SpringSecurityAdapter

@AutoConfiguration
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftAdminSpringSecurityConfig(
    private val properties: KraftAdminProperties,
    private val env: Environment,
) {

    private val log = KraftAdminLogging.logger(javaClass)

    @Bean
    @ConditionalOnMissingBean
    fun adminSecurityConfig(): AdminSecurityConfig {
        val configuredRoles = properties.security.requiredRoles

        require(configuredRoles.isNotEmpty()) {
            "kraftadmin.security.required-roles is not configured. " +
                    "This application must explicitly define which role(s) grant access " +
                    "to the admin panel — there is no safe default. " +
                    "Set e.g. kraftadmin.security.required-roles[0]=ROLE_ADMIN in your configuration."
        }

        val basePath = properties.basePath.removeSuffix("/")

        val normalizedProtectedRoutes = properties.security.protectedRoutes
            .mapKeys { (pattern, _) ->
                if (pattern.startsWith(basePath)) pattern
                else "$basePath${if (pattern.startsWith("/")) "" else "/"}$pattern"
            }

        return AdminSecurityConfig(
            requiredRoles = configuredRoles,
            protectedRoutes = normalizedProtectedRoutes,
            frameworkSecurityActiveCheck = { isSpringSecurityActive() },
            authMode = if (isSpringSecurityActive()) "bridge" else "standalone",
            allowedUsers = properties.security.allowedUsers,
            userPermissions = properties.security.userPermissions,
        )
    }

    @Bean
    fun adminSecurityFilter(
        chain: SecurityProviderChain,
        sessionConfig: SessionConfig,
    ): FilterRegistrationBean<AdminSecurityFilter> {
        val secConfig = adminSecurityConfig()
        log.info(
            "KraftAdmin security wiring — requiredRoles={}, allowedUsers={}, userPermissions={}, features.readOnly={}, features.allowDelete={}",
            secConfig.requiredRoles, secConfig.allowedUsers, secConfig.userPermissions,
            properties.features.readOnly, properties.features.allowDelete
        )
        val registration = FilterRegistrationBean(
            AdminSecurityFilter(
                chain,
                securityConfig = secConfig,
                sessionConfig = sessionConfig,
                featureConfig = properties.features,
            )
        )
        registration.addUrlPatterns("/admin/*")
        registration.order = 100
        return registration
    }

    @Bean
    fun adminSessionStore(config: AdminSecurityConfig): AdminSessionStore =
        AdminSessionStore(config.sessionConfig)

    @Bean
    fun sessionConfig(): SessionConfig = DefaultSessionConfig(
        cookieName = properties.security.cookieName,
        expiryMinutes = properties.security.sessionExpiryMinutes,
    )

    @Bean
    fun builtinBasicAuthProvider(): BuiltinBasicAuthProvider =
        BuiltinBasicAuthProvider(properties.security.basicAuth)

    /**
     * NESTED @Configuration, guarded by @ConditionalOnClass(AuthenticationManager).
     *
     * This MUST stay a separate (nested is fine) configuration class, not a
     * plain @Bean method on the outer class. @ConditionalOnClass on a
     * configuration class is checked by ConfigurationClassPostProcessor
     * BEFORE any of that class's @Bean methods are parsed/registered —
     * so when spring-security-core is absent, Spring never attempts to
     * reflect over AuthenticationManager at all for this class's methods.
     *
     * @ConditionalOnBean/@ConditionalOnMissingBean on an individual @Bean
     * method are NOT equivalent protection: those conditions run AFTER
     * Spring has already reflectively resolved that method's generic
     * signature to predict its bean type (needed for bean-type indexing,
     * used by every OTHER @ConditionalOnMissingBean check anywhere in the
     * context, e.g. kraftAdminContextFilter below) — and that resolution
     * itself throws ClassNotFoundException if the type genuinely isn't on
     * the classpath, regardless of what conditions guard the method.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.security.authentication.AuthenticationManager"])
    class SpringSecurityAdapterConfig {

        private val log = KraftAdminLogging.logger(javaClass)

        @Bean
        fun springSecurityAdapter(
            authenticationManager: ObjectProvider<org.springframework.security.authentication.AuthenticationManager>
        ): SpringSecurityAdapter {
            val manager = authenticationManager.ifAvailable
            if (manager == null) {
                log.warn(
                    "spring-security-core is on the classpath but no AuthenticationManager bean " +
                            "was found. Credential login via SpringSecurityAdapter will be unavailable " +
                            "until one is exposed, e.g.: " +
                            "@Bean fun authenticationManager(c: AuthenticationConfiguration) = c.authenticationManager"
                )
            }
            return SpringSecurityAdapter(manager)
        }
    }

    /**
     * Consumes SpringSecurityAdapter only via ObjectProvider<SpringSecurityAdapter>
     * — this type lives in our own `security` package and carries no
     * problematic generic parameter in ITS signature here, so resolving
     * it never touches AuthenticationManager reflection. If the nested
     * config above was skipped (no spring-security-core), this simply
     * resolves to null.
     */
    @Bean
    fun securityProviderChain(
        sessionStore: AdminSessionStore,
        builtinProvider: BuiltinBasicAuthProvider,
        springSecurityAdapter: ObjectProvider<SpringSecurityAdapter>,
    ): SecurityProviderChain {
        val providers = mutableListOf<AdminSecurityProvider>(SessionSecurityProvider(sessionStore))

        val adapter = springSecurityAdapter.ifAvailable
        if (adapter != null) {
            providers.add(adapter)
        } else {
            providers.add(builtinProvider)
        }

        return SecurityProviderChain(providers.sortedBy { it.priority })
    }


    @Bean
    @ConditionalOnMissingBean
    fun kraftAdminContextFilter(
        securityProviderChain: SecurityProviderChain
    ) = SpringKraftAdminContextFilter(securityProviderChain)

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