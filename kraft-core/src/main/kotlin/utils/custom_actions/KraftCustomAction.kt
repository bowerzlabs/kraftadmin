package com.kraftadmin.utils.custom_actions


/**
 * Base interface for all custom actions in KraftAdmin.
 * Implementations should be Spring @Components to allow for dependency injection.
 */
interface KraftActionHandler<T> {
    fun execute(entity: Any?, params: Map<String, Any?>): KraftActionResponse
}

/**
 * The default placeholder. If this is used, the library can
 * look for a method-level execution or simply log that no logic is attached.
 */
class DefaultKraftActionHandler<T> : KraftActionHandler<T> {
    override fun execute(entity: Any?, params: Map<String, Any?>): KraftActionResponse {
        return KraftActionResponse(
            success = false,
            message = "No custom handler was defined for this action. Please implement KraftActionHandler."
        )
    }
}

data class KraftActionResponse(
    val success: Boolean,
    val message: String,
    val payload: Any? = null,
    val refresh: Boolean = true
)
