package api.utils

import com.kraftadmin.ui_descriptors.KraftActionDescriptor
import com.kraftadmin.ui_descriptors.LookupDescriptor
import kotlin.reflect.KClass

/**
 * Represents a single resource record as returned to the UI.
 *
 * For list views: relatedResources is always null (not fetched).
 * For detail/single views: relatedResources contains expanded
 * collection relations, each limited to 10 items.
 */
data class ResourceRow(
    val id: String,
    val values: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap(),
    val customActions : List<KraftActionDescriptor> = emptyList(),

    // Only populated by FetchById — null in list views by design
    val relatedResources: Map<String, RelatedCollection>? = null
) {
    data class RelatedCollection(
        val fieldName: String,
        val entityType: String,
        val items: List<RelatedItem>,
        val totalInMemory: Int,
        val limited: Boolean,      // true = "Load more" button should appear in UI
        val lookupDescriptor: LookupDescriptor
    )

    data class RelatedItem(
        val id: String,
        val entityType: String,
        val displayLabel: String,
        val values: Map<String, Any?>
    )
}
