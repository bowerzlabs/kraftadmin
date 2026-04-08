package com.kraftadmin.security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal

/**
 * Per-request security context. Holds the resolved [AdminPrincipal] and
 * provides role-enforcement helpers for use inside admin handlers.
 *
 * Adapters populate this from their request scope (e.g. a request attribute
 * in Spring, a call attribute in Ktor) and expose it via their own
 * framework-specific accessor.
 */
class AdminSecurityContext(val principal: AdminUserDTO) {

    /**
     * Returns true if the principal holds the given role.
     */
    fun hasRole(role: String): Boolean = principal.hasRole(role)

    /**
     * Throws [AdminAccessDeniedException] if the principal does not hold
     * every one of the required roles.
     */
    fun requireRoles(vararg roles: String) {
        val missing = roles.filterNot { principal.hasRole(it) }
        if (missing.isNotEmpty()) {
            throw AdminAccessDeniedException(
                principal = principal,
                requiredRoles = missing.toSet(),
            )
        }
    }

    /**
     * Convenience — require a single role.
     */
    fun requireRole(role: String) = requireRoles(role)
}

/**
 * Thrown by [AdminSecurityContext.requireRoles] when the principal lacks
 * a required role. Adapters catch this and emit a 403 response.
 */
class AdminAccessDeniedException(
    val principal: AdminUserDTO,
    val requiredRoles: Set<String>,
) : RuntimeException(
    "Principal '${principal.username}' is missing roles: $requiredRoles"
)