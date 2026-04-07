package com.kraftadmin.security

import com.kraftadmin.api.responses.AdminUserDTO

/**
 * Uniform principal passed to all admin handlers regardless of framework.
 *
 * [raw] holds the underlying framework-specific principal (e.g. Spring's
 * Authentication) in case an adapter needs to reach through.
 */
data class AdminPrincipal(
    val username: String,
    val roles: Set<String>,
    val metadata: Map<String, String> = emptyMap(),
    val raw: Any? = null,
) {
    fun hasRole(role: String): Boolean = role in roles

    fun toDTO(): AdminUserDTO {
        return AdminUserDTO(
            name = metadata["displayName"] ?: username,
            username = username,
            roles = roles,
            initials = metadata["initials"] ?: username.take(2).uppercase(),
            avatar = metadata["avatarUrl"],
            isBridgeMode = raw != null
        )
    }

}

