package security

import com.kraftadmin.security.AdminRequest
import com.kraftadmin.security.AdminResponse
import com.kraftadmin.security.AdminSecurityProvider
import com.kraftadmin.security.AdminUserDTO

/**
 * Chain of responsibility. Tries each [AdminSecurityProvider] in [priority]
 * order and returns the first non-null principal.
 *
 * If all providers return null, [challenge] is delegated to the first
 * provider in the chain (the highest-priority one owns the 401 shape).
 */
class SecurityProviderChain(providers: List<AdminSecurityProvider>) {

    private val chain: List<AdminSecurityProvider> =
        providers.sortedBy { it.priority }

    init {
        require(chain.isNotEmpty()) {"SecurityProviderChain must have at least one provider" }
    }

    fun authenticate(request: AdminRequest): AdminUserDTO? =
        chain.firstNotNullOfOrNull { it.authenticate(request) }

    fun challenge(request: AdminRequest, response: AdminResponse) =
        chain.first().challenge(request, response)

    /**
     * Asks every provider in the chain if they can identify the current user.
     * The first one to return a non-null Principal wins.
     */
    fun resolveCurrentUser(): AdminUserDTO? {
        return chain.firstNotNullOfOrNull { it.getCurrentUser()?.toDTO() }
    }

    /**
     * Tries each provider's authenticateCredentials in priority order.
     * The FIRST provider that recognizes and validates the credentials
     * wins — the caller (login controller) never branches on auth mode
     * itself. A provider returns null either because it doesn't support
     * credential login (e.g. SSO-redirect-only) or because the specific
     * credentials were rejected; both cases fall through to the next
     * provider, and null overall means "no provider could authenticate
     * this login."
     */
    fun authenticateCredentials(username: String, password: String): AdminUserDTO? =
        chain.firstNotNullOfOrNull { it.authenticateCredentials(username, password) }

}