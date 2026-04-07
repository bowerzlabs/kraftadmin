package com.kraftadmin.security

data class BasicAuthConfig(
    val username: String = "admin",
    val password: String? = null,
    val roles: Set<String> = setOf(),
)