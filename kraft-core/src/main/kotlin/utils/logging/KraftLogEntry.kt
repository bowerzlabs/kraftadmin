package com.kraftadmin.utils.logging

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.enums.KraftLogAction
import com.kraftadmin.enums.KraftLogLevel
import com.kraftadmin.security.AdminPrincipal

data class KraftLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: KraftLogLevel,
    val action: KraftLogAction? = null,
    val resource: String? = null,
    val resourceId: String? = null,
    val actor: AdminUserDTO,
    val message: String,
    val trace: String? = null   // For Exception stack traces
)