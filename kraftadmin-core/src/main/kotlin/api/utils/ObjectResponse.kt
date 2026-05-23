package api.utils

import org.slf4j.LoggerFactory

/**
 * Represent a related resource in the UI.
 * Standardizes how foreign keys are sent to the frontend.
 */
data class ObjectResponse(
    val id: String,
    val displayField: Any? = "Unknown"
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        // Reduced to debug to avoid log bloat in production
        logger.debug("Mapping relation: ID=$id, Label=$displayField")
    }
}