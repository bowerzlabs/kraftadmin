package com.kraftadmin.security

/**
 * Framework-agnostic view of an incoming HTTP request.
 * Adapters construct this from their native request type.
 */
data class AdminRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
) {
    fun header(name: String): String? =
        headers[name] ?: headers[name.lowercase()]
}

/**
 * Minimal response handle used only for issuing auth challenges (401).
 * Adapters implement this against their native response type.
 */
interface AdminResponse {
    fun setStatus(code: Int)
    fun setHeader(name: String, value: String)
    fun setBody(body: String)
}