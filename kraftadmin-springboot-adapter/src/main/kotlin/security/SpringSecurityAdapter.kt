package com.kraftadmin.security

import com.kraftadmin.api.responses.AdminUserDTO
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails


/**
 * Delegates authentication to Spring Security's [SecurityContextHolder].
 *
 * This adapter assumes Spring Security has already run its filter chain
 * and populated the context before the admin filter fires.
 * It simply reads the result and translates it into [AdminPrincipal].
 */
class SpringSecurityAdapter : AdminSecurityProvider {
    private val logger = LoggerFactory.getLogger(SpringSecurityAdapter::class.java)

    override val priority: Int = 10

    override fun authenticate(request: AdminRequest): AdminUserDTO? {
        logger.info("using spring security adapter to authenticate")
        val auth = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?: return null

        logger.debug("Current Auth in Context: {} (Authenticated: {})", auth.javaClass.simpleName,
            auth.isAuthenticated
        )

        // If the principal is a String "anonymousUser", return null.
        // This forces the library to treat the request as unauthenticated.
        if (auth.name == "anonymousUser" || auth.principal == "anonymousUser") {
            return null
        }

        logger.info("Authenticated user {}", extractUsername(auth))

        val roles = auth.authorities
            .map { it.authority }
            .toSet()

        logger.info("Roles: {}", roles)


        return AdminPrincipalMapper.toDTO(AdminPrincipal(
            username = extractUsername(auth),
            roles = roles,
            raw = auth,
        ))
    }

    private fun extractUsername(auth: Authentication): String {
        return when (val principal = auth.principal) {
            is UserDetails -> principal.username
            // Handles OAuth2User and OidcUser (Common for Google/GitHub login)
            is AuthenticatedPrincipal -> principal.name
            // Handles JWTs where the principal might be a Jwt object
            is Map<*, *> -> (principal["sub"] ?: principal["username"] ?: auth.name).toString()
            is String -> principal
            else -> auth.name
        }
    }

    override fun challenge(request: AdminRequest, response: AdminResponse) {
        // Spring Security owns the 401 — we should not reach here in normal
        // operation because Spring's filter chain fires before ours.
        // Emit a minimal response just in case.
        response.setStatus(401)
        response.setHeader("WWW-Authenticate", "Bearer")
        response.setBody("Unauthorized")
    }

//    override fun getCurrentUser(): AdminPrincipal? {
//        val auth = SecurityContextHolder.getContext().authentication
//            ?.takeIf { it.isAuthenticated && it.name != "anonymousUser" }
//            ?: return null
//
//        return AdminPrincipal(
//            username = extractUsername(auth),
//            roles = auth.authorities.map { it.authority }.toSet(),
//            raw = auth
//        )
//    }

    private fun extractMetadata(auth: Authentication): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        val principal = auth.principal

        // 1. Resolve Display Name
        val displayName = when (principal) {
            is UserDetails -> principal.username.substringBefore("@")
            // If using OAuth2 (Google/GitHub), check for "name" attribute
            is Map<*, *> -> (principal["name"] ?: principal["given_name"] ?: auth.name).toString()
            else -> auth.name.substringBefore("@")
        }
        metadata["displayName"] = displayName

        // 2. Generate Initials
        metadata["initials"] = displayName.split(" ", ".")
            .filter { it.isNotEmpty() }
            .let { parts ->
                if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}"
                else displayName.take(2)
            }.uppercase()

        return metadata
    }

    override fun getCurrentUser(): AdminPrincipal? {
        val auth = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated && it.name != "anonymousUser" }
            ?: return null

        return AdminPrincipal(
            username = extractUsername(auth),
            roles = auth.authorities.map { it.authority }.toSet(),
            metadata = extractMetadata(auth), // Pass the resolved map
            raw = auth
        )
    }

}