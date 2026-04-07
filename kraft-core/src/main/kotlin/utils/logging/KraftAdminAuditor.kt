package com.kraftadmin.utils.logging

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.enums.KraftLogAction
import com.kraftadmin.security.AdminPrincipal

interface KraftAdminAuditor {
    fun record(action: KraftLogAction, resource: String, id: String, actor: AdminUserDTO)
}