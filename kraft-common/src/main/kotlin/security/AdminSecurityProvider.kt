package com.kraftadmin.security


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

    /**
     * Validates raw username/password credentials at the LOGIN moment,
     * as opposed to [authenticate] which validates an already-established
     * request (cookie/header/session). Returns null if this provider
     * doesn't support credential-based login at all (e.g. it only reads
     * an existing SecurityContext, or auth here is redirect-only SSO).
     *
     * Providers that DO support this are responsible for confirming the
     * user actually exists and is valid against whatever mechanism they
     * represent — AuthenticationManager.authenticate() naturally does
     * this (throws BadCredentialsException / UsernameNotFoundException
     * if not), regardless of whether that manager is backed by a JWT
     * issuer, DB-backed UserDetailsService, LDAP, or an OAuth2 resource
     * server. The caller (KraftAdminAuthController) never needs to know
     * which.
     */
    fun authenticateCredentials(username: String, password: String): AdminUserDTO? = null
}