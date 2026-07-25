package com.kraftadmin.security

import com.kraftadmin.logging.KraftAdminLogging
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID

/**
 * Built-in HTTP Basic auth fallback. Used when no framework security is
 * detected and no custom provider is supplied.
 *
 * If [password] is not set in [BasicAuthConfig], one is auto-generated
 * and printed to stdout at startup — loud by design.
 */
class BuiltinBasicAuthProvider(
    config: BasicAuthConfig
) : AdminSecurityProvider {
    private val logger = KraftAdminLogging.logger(javaClass)
    private val username: String = config.username
    private val roles = config.roles

    override val priority: Int = Int.MAX_VALUE

    init {
        logger.info("Builtin BasicAuthProvider initialized! $config")
    }

    override fun getCurrentUser(): AdminPrincipal {
        // In standalone mode, we might need to access the current
        // HttpServletRequest to find the cookie.
        // This is where a ThreadLocal or RequestContextHolder comes in.
        return AdminPrincipal(username = username, roles = roles)
    }

//    private val password: String = config.password ?: autoGenerate(config.username)
//
//    override fun authenticate(request: AdminRequest): security.AdminUserDTO? {
//        val encoded = request.header("Authorization")
//            ?.takeIf { it.startsWith("Basic ") }
//            ?.removePrefix("Basic ")
//            ?: return null
//
//        val decoded = runCatching {
//            String(Base64.getDecoder().decode(encoded))
//        }.getOrNull() ?: return null
//
//        val (user, pass) = decoded.split(":", limit = 2)
//            .takeIf { it.size == 2 } ?: return null
//
//        if (user != username || pass != password) return null
//
//        return AdminPrincipal(username = user, roles = setOf("ROLE_ADMIN")).toDTO()
//    }

    private val password by lazy {
        config.password ?: autoGenerate(config.username)
    }

    override fun authenticate(request: AdminRequest): AdminUserDTO? {
        logger.info("using builtin authentication $request")
        val authHeader = request.header("Authorization") ?: return null
        if (!authHeader.startsWith("Basic ")) return null

        val decoded = runCatching {
            String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        }.getOrNull()?.split(":", limit = 2) ?: return null

        if (decoded.size < 2) return null

        val (user, pass) = decoded
        logger.info("authenticated user {}, pass {}, username {}, password {}", user, pass, username, password)
        // Accessing 'password' here triggers the lazy log if null
        if (user == username && pass == password) {
            logger.info("Matches")
            logger.info("Authenticated user {}, pass {}", username, pass)
            return AdminPrincipal(username = user, roles = setOf("ROLE_ADMIN")).toDTO()
        } else {
            return null
        }
    }


    override fun challenge(request: AdminRequest, response: AdminResponse) {
        response.setStatus(401)
        response.setHeader("WWW-Authenticate", """Basic realm="Admin Dashboard"""")
        response.setBody("Unauthorized")
    }

    override fun authenticateCredentials(username: String, password: String): AdminUserDTO? {
        if (username != this.username || password != this.password) return null
        return AdminPrincipal(username = username, roles = setOf("ROLE_ADMIN")).toDTO()
    }

    private fun autoGenerate(user: String): String {
        val pwd = UUID.randomUUID().toString().take(12)
        // This only prints if effectivePassword is accessed (i.e., when this provider is active)
        println("""
            [KRAFT] No security framework detected. Falling back to Basic Auth.
            [KRAFT] Username: $user
            [KRAFT] Password: $pwd
            [KRAFT] To override, set 'kraft.security.basic-auth.password' in your YAML.
        """.trimIndent())
        return pwd
    }
}