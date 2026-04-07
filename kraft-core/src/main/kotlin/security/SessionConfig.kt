package security

/**
 * Configuration for the library-managed session.
 * Used only when the library owns authentication (no framework security active).
 *
 * Sessions are stored in-memory — intentionally simple. One admin user,
 * low traffic, restart-invalidation is acceptable behaviour.
 *
 * [cookieName]    HttpOnly cookie that carries the opaque session token.
 * [expiryMinutes] How long a session lives before it expires.
 */
data class SessionConfig(
    val cookieName: String = "adminlib_session",
    val expiryMinutes: Long = 60,
)