package controller

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.security.AdminRequest
import com.kraftadmin.security.AdminSessionStore
import com.kraftadmin.security.SessionConfig
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Conditional
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import security.*
import java.util.*

/**
 * Handles login and logout for library-managed authentication.
 * Only registered when the library owns auth (no framework security active).
 *
 * POST /admin/api/auth/login
 *   Body : { "username": "admin", "password": "s3cret" }
 *   200  : { "message": "Login successful" }
 *   401  : { "error": "Invalid credentials" }
 *
 * POST /admin/api/auth/logout
 *   200  : { "message": "Logged out" }
 *
 * Credentials are validated against the full [SecurityProviderChain] —
 * whichever provider is active (builtin basic auth or custom) handles it.
 * On success, a server-side session is created and its token is written
 * as an HttpOnly cookie. No token is exposed in the response body.
 */
@RestController
@RequestMapping("\${kraftadmin.base-path:/admin}/api/auth")
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftAdminSpringbootAuthController(
    private val chain: SecurityProviderChain,
    private val sessionStore: AdminSessionStore,
    private val sessionConfig: SessionConfig,
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    @PostMapping("/login")
    fun login(
        @RequestBody credentials: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Map<String, String>> {

        val principal = chain.authenticateCredentials(credentials.username, credentials.password)

        if (principal == null) {
            logger.warn("Failed login attempt for username '{}'", credentials.username)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Invalid credentials"))
        }

        // KraftAdmin's own session, separate from whatever cookie/token
        // the provider that validated this login would otherwise issue.
        val token = sessionStore.create(principal)
        response.addCookie(sessionCookie(token, request.isSecure))

        logger.info("Admin login successful for '{}'", principal.username)
        return ResponseEntity.ok(mapOf("message" to "Login successful"))
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, String>> {
        request.cookies
            ?.firstOrNull { it.name == sessionConfig.cookieName }
            ?.let { sessionStore.invalidate(it.value) }
        response.addCookie(expiredCookie())
        return ResponseEntity.ok(mapOf("message" to "Logged out"))
    }

    private fun sessionCookie(token: String, secure: Boolean) =
        Cookie(sessionConfig.cookieName, token).apply {
            isHttpOnly = true; this.secure = secure; path = "/admin"; maxAge = (sessionConfig.expiryMinutes * 60).toInt()
        }

    private fun expiredCookie() =
        Cookie(sessionConfig.cookieName, "").apply { isHttpOnly = true; path = "/admin"; maxAge = 0 }
}

class LoginRequest {
    var username: String = ""
    var password: String = ""
    override fun toString(): String = "username: $username, password: [PROTECTED]"
}