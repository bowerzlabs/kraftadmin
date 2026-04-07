package com.kraftadmin.security

data class BasicAuthConfig(
    var username: String = "admin",
    var password: String? = null,
    var roles: Set<String> = mutableSetOf()
)