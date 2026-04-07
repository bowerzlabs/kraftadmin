package com.kraftadmin.api.responses

data class AdminUserDTO(
    val name: String,
    val username: String,
    val roles: Set<String>,
    val initials: String,
    val avatar: String?,
    val isBridgeMode: Boolean
){
    fun hasRole(role: String): Boolean = role in roles
}