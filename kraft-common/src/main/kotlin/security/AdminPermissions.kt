package com.kraftadmin.security

object AdminPermissions {
    const val DELETE = "delete"
    const val WRITE = "write"
    const val READ_ONLY = "read-only"
    const val MANAGE_USERS = "manage-users"
    const val MANAGE_SETTINGS = "manage-settings"
}

/**
 * Resolves the effective permission set for a given username, falling
 * back to config.features when no explicit override exists.
 */
fun AdminSecurityConfig.permissionsFor(username: String): Set<String> =
    userPermissions.entries
        .firstOrNull { it.key.equals(username, ignoreCase = true) }
        ?.value
        ?: emptySet()

fun AdminSecurityConfig.isUserAllowed(username: String): Boolean =
    allowedUsers.isEmpty() || allowedUsers.any { it.equals(username, ignoreCase = true) }