package security

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.security.AdminPrincipal
import com.kraftadmin.security.AdminRequest
import com.kraftadmin.security.AdminResponse
import com.kraftadmin.security.AdminSecurityProvider
import com.kraftadmin.security.AdminUserDTO
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticatedPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails

/**
 * Delegates authentication to Spring Security's [SecurityContextHolder].
 *
 * This adapter assumes Spring Security has already run its filter chain
 * and populated the context before the admin filter fires. It simply reads
 * the result and translates it into an [AdminUserDTO].
 *
 * IMPORTANT: this adapter answers "is there an authenticated Spring Security
 * principal?" — it does NOT answer "is this principal allowed into /admin?".
 * That decision is made later, by [AdminSecurityFilter] comparing
 * [AdminUserDTO.roles] against the configured required/protected-route
 * roles. A principal authenticated for a completely different part of the
 * host application (e.g. a TALENT-role user signed in for the main app)
 * will still be returned here — role-based rejection happens downstream,
 * not in this adapter. Do not add role filtering here; keep authentication
 * and authorization as separate, independently testable steps.
 */
class SpringSecurityAdapter(
    private val authenticationManager: AuthenticationManager? = null,
) : AdminSecurityProvider {
    private val logger = KraftAdminLogging.logger(javaClass)

    init {
        logger.info("using springsecurity adapter to authenticate")
    }

    override val priority: Int = 10

    override fun authenticate(request: AdminRequest): AdminUserDTO? {
        val auth = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?: return null

        // If the principal is a String "anonymousUser", return null.
        // This forces the library to treat the request as unauthenticated.
        if (auth.name == "anonymousUser" || auth.principal == "anonymousUser") {
            return null
        }

        val roles = auth.authorities
            .map { it.authority }
            .toSet()

        return AdminPrincipalMapper.toDTO(
            AdminPrincipal(
                username = extractUsername(auth),
                roles = roles,
                raw = auth,
            )
        )
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

    private fun extractMetadata(auth: Authentication): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        val principal = auth.principal

        val displayName = when (principal) {
            is UserDetails -> principal.username.substringBefore("@")
            is Map<*, *> -> (principal["name"] ?: principal["given_name"] ?: auth.name).toString()
            else -> auth.name.substringBefore("@")
        }
        metadata["displayName"] = displayName

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
            metadata = extractMetadata(auth),
            raw = auth
        )
    }

    override fun authenticateCredentials(username: String, password: String): AdminUserDTO? {
        val manager = authenticationManager ?: run {
            logger.debug("No AuthenticationManager available — this host may rely on redirect-only SSO, which SpringSecurityAdapter cannot service directly.")
            return null
        }

        return try {
            val result: Authentication = manager.authenticate(
                UsernamePasswordAuthenticationToken(username, password)
            )
            if (!result.isAuthenticated) return null

            val roles = result.authorities.map { it.authority }.toSet()
            logger.info("Credential login succeeded for '{}' against host AuthenticationManager", result.name)

            AdminPrincipalMapper.toDTO(
                AdminPrincipal(username = extractUsername(result), roles = roles, raw = result)
            )
        } catch (e: BadCredentialsException) {
            logger.warn("Credential login failed for '{}': bad credentials", username)
            null
        } catch (e: Exception) {
            // Covers UsernameNotFoundException, DisabledException, LockedException,
            // etc. — all mean "the parent app says this login isn't valid,"
            // regardless of which AuthenticationProvider raised it.
            logger.warn("Credential login failed for '{}': {}", username, e.message)
            null
        }
    }

}