package util

import security.AdminUserDTO
import logging.KraftLogAction
import logging.KraftAdminAuditor
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import telemetry.KraftTelemetryService
import org.springframework.stereotype.Service

import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import com.kraftadmin.model.KraftTelemetryEvent
import com.kraftadmin.model.TelemetryType

@Service
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
class KraftSpringLoggingAuditor(
    private val telemetryService: KraftTelemetryService
) : KraftAdminAuditor {

    override fun record(action: KraftLogAction, resource: String, id: String, actor: AdminUserDTO) {
        println("Starting $action $resource $id $actor")
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

        val ua = request?.getHeader("User-Agent")
        val ip = request?.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request?.remoteAddr
        
        val event = KraftTelemetryEvent(
            traceId = request?.getAttribute("traceId") as String,
            id = id,
            type = TelemetryType.AUDIT,
            action = action.toString(),
            resource = resource,
            actor = AdminUserDTO(
                name = actor.name,
                username = actor.username,
                roles = actor.roles,
                initials = actor.initials,
                avatar = actor.avatar,
                isBridgeMode = actor.isBridgeMode,
            ),
            timestamp = System.currentTimeMillis(),
            userAgent = ua,
            ipAddress = ip,
            referer = request?.getHeader("Referer"),
            deviceType = parseDeviceType(ua), // Call your parser here
            status = 200,
            durationMs = 0
        )

        telemetryService.record(event)
        println("Ending $action $resource $id $actor")
    }

    private fun parseDeviceType(ua: String?): String {
        if (ua == null) return "Unknown"
        val lowercaseUa = ua.lowercase()
        return when {
            lowercaseUa.contains("mobile") || lowercaseUa.contains("android") || lowercaseUa.contains("iphone") -> "Mobile"
            lowercaseUa.contains("tablet") || lowercaseUa.contains("ipad") -> "Tablet"
            else -> "Desktop"
        }
    }

}
