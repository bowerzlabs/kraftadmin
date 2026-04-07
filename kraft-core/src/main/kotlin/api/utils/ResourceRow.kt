package api.utils

/**
 * Wraps a single row of data.
 * The 'values' map stores the actual data, but the class
 * provides a place for row-level metadata (like permissions).
 */
data class ResourceRow(
    val id: String,
    val values: Map<String, Any?>,
    val metadata: RowMetadata = RowMetadata()
)

