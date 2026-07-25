package security

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.config.FeatureConfig
import com.kraftadmin.security.AdminAccessDeniedException
import com.kraftadmin.security.AdminPermissions
import com.kraftadmin.security.AdminRequest
import com.kraftadmin.security.AdminSecurityConfig
import com.kraftadmin.security.AdminSecurityContext
import com.kraftadmin.security.SessionConfig
import com.kraftadmin.security.isUserAllowed
import com.kraftadmin.security.permissionsFor
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AdminSecurityFilter(
    private val chain: SecurityProviderChain,
    private val loginPagePath: String = "/admin/#/auth/login",
    private val securityConfig: AdminSecurityConfig,
    private val sessionConfig: SessionConfig,
    /**
     * Optional — when supplied, features.allowDelete/readOnly act as a
     * ceiling over per-user permissions EXCEPT for the settings escape
     * hatch (see MANAGE_SETTINGS below), which is deliberately allowed to
     * bypass a persisted/global readOnly=true. Without that bypass, a
     * readOnly flag that got persisted as true (e.g. via the settings
     * store) can never be corrected through the API itself, since the
     * PUT to /admin/api/settings that would fix it is itself blocked by
     * the flag it's trying to change.
     */
    private val featureConfig: FeatureConfig? = null,
) : Filter {

    private val logger = KraftAdminLogging.logger(javaClass)

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS")
    private val deleteMethods = setOf("DELETE")

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        filterChain: FilterChain,
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        val uri = httpRequest.requestURI
        val method = httpRequest.method.uppercase()

        val isAuthApi = uri.startsWith("/admin/api/auth/")
        val isStaticAsset = uri.contains("/admin/assets/") ||
                uri.startsWith("/admin/files/") ||
                uri.endsWith(".js") ||
                uri.endsWith(".css") ||
                uri.endsWith(".ico")

        val isUnauthenticatedPath = uri in UNAUTHENTICATED_PATHS ||
                uri == "/admin" ||
                uri == "/admin/"

        if (isAuthApi || isStaticAsset || isUnauthenticatedPath) {
            filterChain.doFilter(request, response)
            return
        }

        val adminRequest = httpRequest.toAdminRequest(sessionConfig)
        val principal = chain.authenticate(adminRequest)

        logger.info("authenticated admin $principal")

        if (principal == null) {
            handleUnauthenticated(httpRequest, httpResponse)
            return
        }

        val globalRoles = securityConfig.requiredRoles
        if (globalRoles.isEmpty()) {
            writeForbidden(httpResponse, "Access control misconfigured.")
            return
        }

        val hasGlobalAccess = principal.roles.any { it in globalRoles }
        if (!hasGlobalAccess) {
            writeForbidden(httpResponse, "You do not have the required permissions.")
            return
        }

        // Allowlist: role membership alone (e.g. shared ROLE_USER across
        // 1000 accounts) is not sufficient — this narrows to explicitly
        // permitted usernames when the allowlist is non-empty.
        if (!securityConfig.isUserAllowed(principal.username)) {
            logger.warn("User '{}' has required role but is not in the admin allowlist", principal.username)
            writeForbidden(httpResponse, "Your account is not authorized for admin access.")
            return
        }

        val userPermissions = securityConfig.permissionsFor(principal.username)

        val routeRoles = resolveRouteRoles(uri)
        if (routeRoles != null && method !in safeMethods) {
            // A route's required "role" set can be satisfied either by a
            // real Spring role OR by a KraftAdmin permission string (e.g.
            // configuring protected-routes./admin/api/settings/**=manage-settings
            // and granting that as a userPermissions entry, independent
            // of the host app's role model entirely).
            val hasRouteAccess = principal.roles.any { it in routeRoles } ||
                    userPermissions.any { it in routeRoles }
            if (!hasRouteAccess) {
                writeForbidden(httpResponse, "You have read-only access to this resource.")
                return
            }
        }

        val globallyReadOnly = featureConfig?.readOnly == true
        val globallyDeleteAllowed = featureConfig?.allowDelete ?: true

        // Escape hatch: settings management is exempt from the global
        // read-only ceiling for users explicitly granted MANAGE_SETTINGS.
        // This is the ONLY bypass of globallyReadOnly anywhere in this
        // filter — everything else still treats featureConfig.readOnly as
        // a hard ceiling per the class doc above.
        val isSettingsRoute = uri.contains("/api/settings")
        val bypassesReadOnly = isSettingsRoute && userPermissions.contains(AdminPermissions.MANAGE_SETTINGS)

        logger.info(
            "Permission check — user='{}', method='{}', uri='{}', userPermissions={}, globallyReadOnly={}, bypassesReadOnly={}",
            principal.username, method, uri, userPermissions, globallyReadOnly, bypassesReadOnly
        )

        val effectivelyReadOnly = !bypassesReadOnly &&
                (globallyReadOnly || userPermissions.contains(AdminPermissions.READ_ONLY))

        if (effectivelyReadOnly && method !in safeMethods) {
            writeForbidden(httpResponse, "Your account has read-only access.")
            return
        }

        if (method in deleteMethods && !globallyDeleteAllowed) {
            writeForbidden(httpResponse, "Delete is disabled for this deployment.")
            return
        }

        val context = AdminSecurityContext(principal)
        httpRequest.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        httpRequest.setAttribute(CONTEXT_ATTRIBUTE, context)
        httpRequest.setAttribute(PERMISSIONS_ATTRIBUTE, userPermissions)

        try {
            filterChain.doFilter(request, response)
        } catch (e: AdminAccessDeniedException) {
            httpResponse.status = HttpServletResponse.SC_FORBIDDEN
            httpResponse.contentType = "application/json"
            httpResponse.writer.write("{\"error\":\"Forbidden\",\"detail\":\"${e.message}\"}")
        }
    }

    private fun resolveRouteRoles(uri: String): Set<String>? {
        return securityConfig.protectedRoutes.entries
            .filter { (prefix, _) -> uri.startsWith(prefix.removeSuffix("/**")) }
            .maxByOrNull { (prefix, _) -> prefix.length }
            ?.value
    }

    private fun writeForbidden(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        val safeMessage = message.replace("\"", "\\\"")
        response.writer.write("{\"error\":\"Forbidden\",\"message\":\"$safeMessage\"}")
    }

    private fun handleUnauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val uri = request.requestURI
        val acceptsHtml = request.getHeader("Accept")?.contains("text/html") == true
        val isApiRequest = uri.contains("/api/")

        if (acceptsHtml && !isApiRequest) {
            if (uri != "/admin/" && uri != "/admin") {
                response.sendRedirect("/admin/")
            } else {
                response.status = 401
            }
        } else {
            response.status = 401
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"Unauthorized","message":"Session expired or invalid","authMode":"${securityConfig.authMode}"}"""
            )
        }
    }

    companion object {
        const val PRINCIPAL_ATTRIBUTE = "kraftadmin.principal"
        const val CONTEXT_ATTRIBUTE = "kraftadmin.context"
        const val PERMISSIONS_ATTRIBUTE = "kraftadmin.permissions"

        val UNAUTHENTICATED_PATHS = setOf(
            "/admin/",
            "/admin",
            "/admin/index.html",
            "/admin/api/auth/login",
            "/admin/api/auth/logout"
        )
    }
}

private fun HttpServletRequest.toAdminRequest(sessionConfig: SessionConfig): AdminRequest {
    val headers = headerNames.asSequence()
        .associateWith { getHeader(it) }
        .toMutableMap()

    cookies?.firstOrNull { it.name == sessionConfig.cookieName }?.let {
        headers["X-Admin-Session"] = it.value
    }

    return AdminRequest(method, requestURI, headers)
}