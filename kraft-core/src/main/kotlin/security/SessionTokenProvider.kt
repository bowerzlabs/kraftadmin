package security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal
import config.KraftAdminPropertiesConfig
import security.AdminSessionStore

/**
 * Authenticates requests carrying a library-issued session cookie.
 * Looks up the session token in [security.AdminSessionStore] and returns the
 * stored principal if the session is valid and not expired.
 *
 * Priority 50 — runs after custom providers (0) and framework adapters (10),
 * but before BuiltinBasicAuthProvider (Int.MAX_VALUE).
 */
class SessionTokenProvider(
    private val sessionStore: AdminSessionStore,
    private val sessionConfig: SessionConfig,
    private val kraftAdminPropertiesConfig: KraftAdminPropertiesConfig
) : AdminSecurityProvider {

    override val priority: Int = 50

    override fun getCurrentUser(): AdminPrincipal {
        // In standalone mode, we might need to access the current
        // HttpServletRequest to find the cookie.
        // This is where a ThreadLocal or RequestContextHolder comes in.
//        return null // Logic depends on how you store the active request
        return AdminPrincipal(username = kraftAdminPropertiesConfig.security.basicAuth.username, roles = kraftAdminPropertiesConfig.security.basicAuth.roles)
    }

    override fun authenticate(request: AdminRequest): AdminUserDTO? {
        val token = request.header("Cookie")
            ?.split(";")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("${sessionConfig.cookieName}=") }
            ?.removePrefix("${sessionConfig.cookieName}=")
            ?: return null

        return sessionStore.get(token)
    }

    override fun challenge(request: AdminRequest, response: AdminResponse) {
        response.setStatus(401)
        response.setBody("Unauthorized")
    }
}