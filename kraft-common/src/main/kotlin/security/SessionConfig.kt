package com.kraftadmin.security

/**
 * Configuration for the library-managed session.
 * Used only when the library owns authentication (no framework security active,
 * or bridge mode authenticating via SpringSecurityAdapter.authenticateCredentials).
 *
 * Sessions are stored in-memory — intentionally simple. One admin user,
 * low traffic, restart-invalidation is acceptable behaviour.
 */
interface SessionConfig {
    /** HttpOnly cookie that carries the opaque session token. */
    var cookieName: String
    /** How long a session lives before it expires. */
    var expiryMinutes: Long
}

/** Plain default impl for non-Spring platforms constructing AdminSecurityConfig directly. */
class DefaultSessionConfig(
    override var cookieName: String = "adminlib_session",
    override var expiryMinutes: Long = 60,
) : SessionConfig