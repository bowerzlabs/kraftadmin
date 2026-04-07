package api.utils

/**
 * Represents an @Embedded object (like Address, Bio, etc.)
 * Provides a summarized string for table views and the full map for forms.
 */
data class EmbeddedResponse(
    val summary: String,
    val data: Map<String, Any?>
)