package security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal

class SessionSecurityProvider(
    private val sessionStore: AdminSessionStore,
    private val cookieName: String = "adminlib_session",
    override val priority: Int = 0 // Should be high priority to catch existing sessions first
) : AdminSecurityProvider {

    override fun authenticate(request: AdminRequest): AdminUserDTO? {
        // Look for the specific header we mapped from the cookie in the Filter
        val sessionId = request.headers["X-Admin-Session"]
            ?: return null // No session header, let the next provider try

        // Validate against the store
        return sessionStore.get(sessionId)
    }

    override fun challenge(request: AdminRequest, response: AdminResponse) {
        // Sessions usually don't 'challenge' like Basic Auth does,
        // they just fail and let the filter handle the 401.
    }

    override fun getCurrentUser(): AdminPrincipal? {
        // In standalone mode, we might need to access the current
        // HttpServletRequest to find the cookie.
        // This is where a ThreadLocal or RequestContextHolder comes in.
        return null // Logic depends on how you store the active request
    }

}