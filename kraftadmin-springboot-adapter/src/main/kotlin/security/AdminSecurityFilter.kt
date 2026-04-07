package com.kraftadmin.security

import security.SecurityProviderChain
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import security.AdminAccessDeniedException
import security.AdminRequest
import security.AdminSecurityConfig
import security.AdminSecurityContext

///**
// * Servlet filter that guards all /admin/** routes using the
// * [SecurityProviderChain].
// *
// * Unauthenticated request handling:
// *  - Browser navigation (Accept: text/html) → 302 redirect to [loginPagePath]
// *  - API / fetch calls (Accept: application/json) → 401 JSON, no WWW-Authenticate
// *    header so the browser never shows the native credential popup
// *
// * [loginPagePath] is the Svelte login page served by the frontend — distinct
// * from the API login endpoint at /admin/api/auth/login.
//*/
class AdminSecurityFilter(
    private val chain: SecurityProviderChain,
    private val loginPagePath: String = "/admin/#/auth/login",
    private val securityConfig: AdminSecurityConfig = AdminSecurityConfig(),
) : Filter {

    private val logger = LoggerFactory.getLogger(AdminSecurityFilter::class.java)

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        filterChain: FilterChain,
    ) {

        logger.info("filtering request: $request, response: $response, filter chain: $filterChain")
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse
        val uri = httpRequest.requestURI

        // Check for Public Paths AND Static Assets
        val isAuthApi = uri.startsWith("/admin/api/auth/")
        val isStaticAsset = uri.contains("/admin/assets/") ||
                uri.endsWith(".js") ||
                uri.endsWith(".css") ||
                uri.endsWith(".ico")

        //  Check UNAUTHENTICATED_PATHS (using startsWith for the API)
        val isUnauthenticatedPath = uri in UNAUTHENTICATED_PATHS ||
                uri == "/admin" ||
                uri == "/admin/"

        if (isAuthApi || isStaticAsset || isUnauthenticatedPath) {
            filterChain.doFilter(request, response)
            return
        }

       // Public paths (login page + auth API) pass through unconditionally
        if (httpRequest.requestURI in UNAUTHENTICATED_PATHS) {
            filterChain.doFilter(request, response)
            return
        }

        val adminRequest = httpRequest.toAdminRequest()
        val principal    = chain.authenticate(adminRequest)

        logger.info("adminRequest $adminRequest")
        println("Principle: $principal")

        if (principal == null) {
            handleUnauthenticated(httpRequest, httpResponse)
            return
        }

        val requiredRoles = securityConfig.requiredRoles
        val hasAccess = principal.roles.any { it in requiredRoles }

        if (!hasAccess) {
            logger.warn("User {} has roles {}, but access requires one of {}",
                principal.username, principal.roles, requiredRoles)

            // Return 403 Forbidden instead of 401
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error": "Forbidden", "message": "You do not have the required permissions."}""")
            return
        }

        val context = AdminSecurityContext(principal)
        httpRequest.setAttribute(PRINCIPAL_ATTRIBUTE, principal)
        httpRequest.setAttribute(CONTEXT_ATTRIBUTE, context)

        try {
            filterChain.doFilter(request, response)
        } catch (e: AdminAccessDeniedException) {
            httpResponse.status = 403
            httpResponse.contentType = "application/json"
            httpResponse.writer.write("""{"error":"Forbidden","detail":"${e.message}"}""")
        }
    }

    /**
     * Decides how to respond to an unauthenticated request:
     *
     * - Browser navigations (Accept contains text/html) get a redirect to the
     *   Svelte login page. This is the path a user hits when they open the
     *   dashboard URL in a fresh browser tab.
     *
     * - API/fetch requests get a plain 401 JSON with no WWW-Authenticate header.
     *   Omitting WWW-Authenticate is what suppresses the browser's native popup —
     *   the Svelte app catches the 401 and navigates to the login page itself.
     */
    private fun handleUnauthenticated(
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        // --- DEBUG LOGGING BLOCK ---
        val cookieString = request.cookies?.joinToString("; ") { "${it.name}=${it.value}" } ?: "none"
        val headersString = request.headerNames.asSequence()
            .map { "$it: ${request.getHeader(it)}" }
            .joinToString("\n      ")

        println("""
    --- Unauthenticated Request ---
    URI:     ${request.method} ${request.requestURI}
    Cookies: $cookieString
    Headers: 
      $headersString
    -------------------------------
    """.trimIndent())
        // ----------------------------

        val uri = request.requestURI
        val acceptsHtml = request.getHeader("Accept")?.contains("text/html") == true
        val isApiRequest = uri.contains("/api/")

        if (acceptsHtml && !isApiRequest) {
            if (uri != "/admin/" && uri != "/admin") {
                response.sendRedirect("/admin/")
            } else {
                // Serve the index.html but mark as 401 so JS knows the state
                response.status = 401
            }
        } else {
            response.status = 401
            response.contentType = "application/json"
            response.writer.write("""{"error":"Unauthorized","message":"Session expired or invalid"}""")
        }
    }

    /**
     * Builds the login page URL, preserving the original path as a
     * [returnTo] query parameter so the Svelte app can redirect back
     * after a successful login.
     */
    private fun buildRedirectUrl(request: HttpServletRequest): String {
        val originalPath = request.requestURI
//        return "$loginPagePath?returnTo=${encode(originalPath)}"
        return loginPagePath
    }

    companion object {
        const val PRINCIPAL_ATTRIBUTE = "kraftadmin.principal"
        const val CONTEXT_ATTRIBUTE   = "kraftadmin.context"

        val UNAUTHENTICATED_PATHS = setOf(
            "/admin/",                // The root must be allowed so the JS can load
            "/admin",                 // Handle without trailing slash
            "/admin/index.html",      // Explicit index
            "/admin/api/auth/login",  // Login API
            "/admin/api/auth/logout"  // Logout API
        )
    }

}

private fun HttpServletRequest.toAdminRequest(): AdminRequest {
//    val headers = headerNames.asSequence().associateWith { getHeader(it) }.toMutableMap()

    val headers = headerNames.asSequence()
        .associateWith { getHeader(it) }
        .toMutableMap()

    // Map cookie to a header the provider understands
    cookies?.firstOrNull { it.name == "adminlib_session" }?.let {
        headers["X-Admin-Session"] = it.value
    }

    return AdminRequest(method, requestURI, headers)
}