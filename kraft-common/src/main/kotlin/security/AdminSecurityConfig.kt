package com.kraftadmin.security

/**
 * Top-level configuration for the admin security system.
 *
 * requiredRoles has NO default. Every consuming application defines its own
 * role model — there is no universally-correct guess for "which role means
 * admin access here." An app using this library MUST explicitly configure
 * kraftadmin.security.required-roles (or supply AdminSecurityConfig directly)
 * to whichever role(s) represent staff/admin access in ITS OWN security setup.
 * Falling back to a baked-in default here would silently grant admin access
 * based on a guess about the parent app's role naming — exactly the class of
 * bug this library must never introduce.
 */
data class AdminSecurityConfig(
    val basicAuth: BasicAuthConfig = BasicAuthConfig(),
    val customProvider: AdminSecurityProvider? = null,
    val sessionConfig: SessionConfig = DefaultSessionConfig(),
    /**
     * Optional override for framework detection logic.
     * Defaults to classpath marker scanning in [security.SecurityProviderResolver].
     */
    val frameworkSecurityActiveCheck: (() -> Boolean)? = null,
    /**
     * Roles permitted to access /admin/ when no per-route override exists
     * in [protectedRoutes]. Deliberately narrow by default — admin access
     * should be opt-in per role, never a broad set of general app roles.
     * A consuming application integrating with an existing Spring Security
     * setup (e.g. a TALENT/USER-role main app) MUST explicitly configure
     * this to whichever role(s) actually represent admin/staff access —
     * never widen this default to include general-purpose application roles.
     */
    val requiredRoles: List<String> = emptyList(),
    /**
     * Per-route-prefix role overrides (e.g. "/api/settings/" -> {"ROLE_SUPERUSER"}).
     * Longest matching prefix wins; falls back to [requiredRoles] when no
     * entry matches. See AdminSecurityFilter.resolveRouteRoles.
     */
    val protectedRoutes: Map<String, Set<String>> = emptyMap(),
    val authMode: String,
    /**
     * Explicit username allowlist. When non-empty, a user must be in this
     * set (in addition to passing requiredRoles) to access ANY /admin
     * route. This is the answer to "role X has 1000 users, only 3 of them
     * should reach the admin panel" — role membership alone is too coarse.
     * Empty (default) means no additional restriction beyond role checks.
     * Matched case-insensitively against AdminUserDTO.username.
     */
    val allowedUsers: Set<String> = emptySet(),
    /**
     * Per-username permission overrides, independent of the host app's
     * own role system. Keys are usernames (case-insensitive), values are
     * permission sets KraftAdmin itself understands (e.g. "delete",
     * "read-only", or custom action names). Checked in AdminSecurityFilter
     * / custom-action handlers AFTER authentication+role+allowlist all
     * pass. Absent entry = default permission set (features.readOnly /
     * features.allowDelete governs, same as today).
     */
    val userPermissions: Map<String, Set<String>> = emptyMap(),
)