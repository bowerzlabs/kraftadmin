package com.kraftadmin.security

import jakarta.servlet.http.HttpServletResponse
import security.AdminResponse

/**
 * Bridges [AdminResponse] to Spring's [HttpServletResponse].
 */
class SpringAdminResponse(
    private val delegate: HttpServletResponse,
) : AdminResponse {
    override fun setStatus(code: Int) { delegate.status = code }
    override fun setHeader(name: String, value: String) { delegate.setHeader(name, value) }
    override fun setBody(body: String) { delegate.writer.write(body) }
}