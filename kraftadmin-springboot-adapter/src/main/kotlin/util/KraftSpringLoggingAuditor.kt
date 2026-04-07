package com.kraftadmin.util

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.enums.KraftLogAction
import com.kraftadmin.enums.KraftLogLevel
import com.kraftadmin.security.AdminPrincipal
import com.kraftadmin.utils.logging.KraftAdminAuditor
import com.kraftadmin.utils.logging.KraftLogEntry
import org.springframework.stereotype.Service

@Service
class KraftSpringLoggingAuditor(
    private val loggingService: KraftSpringLoggingService
) : KraftAdminAuditor {
    override fun record(action: KraftLogAction, resource: String, id: String, actor: AdminUserDTO) {
        val entry = KraftLogEntry(
            level = KraftLogLevel.AUDIT,
            action = action,
            resource = resource,
            resourceId = id,
            actor = actor,
            message = "User ${actor.username} performed $action on $resource ($id)"
        )
        loggingService.push(entry)
    }

}

