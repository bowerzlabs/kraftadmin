package com.kraftadmin.security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory session store for library-managed authentication.
 *
 * Only active when the library owns auth (no framework security detected).
 * Sessions are intentionally not persisted — restart invalidates all sessions,
 * which is acceptable for an admin dashboard with one or few users.
 *
 * Expired sessions are cleaned up lazily on each [get] and [create] call
 * so there is no background thread required.
 */
class AdminSessionStore(private val config: SessionConfig) {

    private data class Entry(
        val principal: AdminUserDTO,
        val expiresAt: Instant,
    )

    private val store = ConcurrentHashMap<String, Entry>()

    /**
     * Creates a new session for [principal] and returns the session token.
     */
    fun create(principal: AdminUserDTO): String {
        purgeExpired()
        val token = UUID.randomUUID().toString()
        store[token] = Entry(
            principal = principal,
            expiresAt = Instant.now().plusSeconds(config.expiryMinutes * 60),
        )
        return token
    }

    /**
     * Returns the [AdminPrincipal] for a valid, non-expired session token,
     * or null if the token is unknown or expired.
     */
    fun get(token: String): AdminUserDTO? {
        purgeExpired()
        val entry = store[token] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(token)
            return null
        }
        return entry.principal
    }

    /**
     * Invalidates a session token immediately.
     */
    fun invalidate(token: String) {
        store.remove(token)
    }

    private fun purgeExpired() {
        val now = Instant.now()
        store.entries.removeIf { (_, entry) -> now.isAfter(entry.expiresAt) }
    }
}