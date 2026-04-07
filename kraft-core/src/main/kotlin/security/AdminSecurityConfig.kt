package security

import com.kraftadmin.security.BasicAuthConfig

/**
 * Top-level configuration for the admin security system.
 *
 * Typical usage (no framework security):
 *   AdminSecurityConfig(basicAuth = BasicAuthConfig(password = "s3cret"))
 *
 * With a custom provider:
 *   AdminSecurityConfig(customProvider = MyApiKeyProvider())
 *
 * With a framework adapter (wired by the adapter module, not the user):
 *   AdminSecurityConfig(frameworkAdapterFactory = { SpringSecurityAdapter(ctx) })
 */
data class AdminSecurityConfig(
    val basicAuth: BasicAuthConfig = BasicAuthConfig(),
    val customProvider: AdminSecurityProvider? = null,
    val sessionConfig: SessionConfig = SessionConfig(),

    /**
     * Supplied by adapter modules (e.g. spring-boot-adapter).
     * Called only when [frameworkSecurityActiveCheck] returns true.
     */
    val frameworkAdapterFactory: (() -> AdminSecurityProvider)? = null,

    /**
     * Optional override for framework detection logic.
     * Defaults to classpath marker scanning in [security.SecurityProviderResolver].
     */
    val frameworkSecurityActiveCheck: (() -> Boolean)? = null,

    val requiredRoles: Set<String> = setOf("ROLE_ADMIN", "ROLE_TALENT", "ROLE_USER"), // Default to ROLE_ADMIN

)