package security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal

/**
 * Core strategy interface. Each provider knows how to:
 *  1. authenticate a request and return a principal (or null if it can't)
 *  2. issue a challenge when authentication fails
 *
 * Providers are tried in [priority] order (lower = tried first).
 * The first non-null result from [authenticate] wins.
 */
interface AdminSecurityProvider {

    /**
     * Attempt to authenticate the request.
     * Return null if this provider cannot handle the request
     * (e.g. wrong scheme, missing header) — the chain will try the next one.
     */
    fun authenticate(request: AdminRequest): AdminUserDTO?

    /**
     * Write a 401 challenge appropriate for this provider.
     * Called when the entire chain returns null.
     */
    fun challenge(request: AdminRequest, response: AdminResponse)

    /**
     * Lower value = tried earlier in the chain.
     * Custom providers default to 0, built-in fallback is Int.MAX_VALUE.
     */
    val priority: Int get() = 100

    /**
     * Retrieves the currently authenticated principal from the
     * underlying framework's context (e.g., SecurityContextHolder,
     * Coroutine Context, or Session Store).
     */
    fun getCurrentUser(): AdminPrincipal?
}