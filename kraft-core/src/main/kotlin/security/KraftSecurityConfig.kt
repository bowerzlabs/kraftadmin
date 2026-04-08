package com.kraftadmin.security

sealed interface KraftSecurityConfig {

    /** No security - purely for local development */
    object None : KraftSecurityConfig

    /** * Standalone / Fallback mode.
     * Protects the dashboard using internal logic without requiring Spring/Ktor Security.
     */
    data class Standalone(
        val username: String = "admin@kraftadmin.com",
        val password: String = "passhash", // Ideally hashed or read from ENV
        val tokenSecret: String = "2026#", // Used to sign the stateless JWT/Token
        val rolesAllowed: Set<String> = setOf("KRAFT_ADMIN")
    ) : KraftSecurityConfig

    /** Bridges to an existing framework (Spring Security/Ktor) */
    data class FrameworkBridged(
        val rolesAllowed: Set<String>
    ) : KraftSecurityConfig
}