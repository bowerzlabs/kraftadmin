package com.kraftadmin.security

import kotlin.collections.plusAssign

/**
 * Runs once at startup. Inspects the classpath and the supplied config to
 * build a [SecurityProviderChain].
 *
 * Resolution order:
 *  1. Explicit custom provider (highest priority, always prepended)
 *  2. Framework adapter (if detected on classpath via [frameworkAdapterFactory])
 *  3. [BuiltinBasicAuthProvider] (always last)
 */
class SecurityProviderResolver(
    private val config: AdminSecurityConfig,
) {
    fun resolve(): SecurityProviderChain {
        val providers = mutableListOf<AdminSecurityProvider>()

        // 1. custom provider — explicit always wins
        config.customProvider?.let { providers += it }

        // 2. framework adapter — supplied by the adapter module at wiring time
        config.frameworkAdapterFactory
            ?.takeIf { isFrameworkSecurityActive() }
            ?.invoke()
            ?.let { providers += it }

        // 3. built-in fallback — always present as last resort
        providers.plusAssign(BuiltinBasicAuthProvider(config.basicAuth))

        return SecurityProviderChain(providers)
    }

    /**
     * Checks whether a known framework security class is on the classpath.
     * Adapters can override or supplement this via [AdminSecurityConfig].
     */
    private fun isFrameworkSecurityActive(): Boolean =
        config.frameworkSecurityActiveCheck?.invoke()
            ?: KNOWN_MARKERS.any { classPresent(it) }

    private fun classPresent(name: String): Boolean = try {
        Class.forName(name, false, javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    companion object {
        private val KNOWN_MARKERS = listOf(
            "org.springframework.security.web.SecurityFilterChain",
            "io.ktor.server.auth.Authentication",
            "io.micronaut.security.filters.SecurityFilter",
            "io.quarkus.security.identity.SecurityIdentity",
        )
    }
}