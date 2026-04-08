package com.kraftadmin.security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal

/**
 * Chain of responsibility. Tries each [com.kraftadmin.security.AdminSecurityProvider] in [priority]
 * order and returns the first non-null principal.
 *
 * If all providers return null, [challenge] is delegated to the first
 * provider in the chain (the highest-priority one owns the 401 shape).
 */
class SecurityProviderChain(providers: List<AdminSecurityProvider>) {

    private val chain: List<AdminSecurityProvider> =
        providers.sortedBy { it.priority }

    init {
        require(chain.isNotEmpty()) { "SecurityProviderChain must have at least one provider" }
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
}