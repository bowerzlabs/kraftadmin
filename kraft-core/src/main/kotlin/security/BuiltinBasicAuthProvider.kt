package security

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal
import com.kraftadmin.security.BasicAuthConfig
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

    private val username: String = config.username
    private val roles = config.roles

    override val priority: Int = Int.MAX_VALUE

    override fun getCurrentUser(): AdminPrincipal {
        // In standalone mode, we might need to access the current
        // HttpServletRequest to find the cookie.
        // This is where a ThreadLocal or RequestContextHolder comes in.
        return AdminPrincipal(username = username, roles = roles)
    }

//    private val password: String = config.password ?: autoGenerate(config.username)
//
//    override fun authenticate(request: AdminRequest): AdminUserDTO? {
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
        val authHeader = request.header("Authorization") ?: return null
        if (!authHeader.startsWith("Basic ")) return null

        val decoded = runCatching {
            String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        }.getOrNull()?.split(":", limit = 2) ?: return null

        if (decoded.size < 2) return null

        val (user, pass) = decoded
        // Accessing 'password' here triggers the lazy log if null
        if (user == username && pass == password) {
            return AdminPrincipal(username = user, roles = setOf("ROLE_ADMIN")).toDTO()
        }
        return null
    }

    override fun challenge(request: AdminRequest, response: AdminResponse) {
        response.setStatus(401)
        response.setHeader("WWW-Authenticate", """Basic realm="Admin Dashboard"""")
        response.setBody("Unauthorized")
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