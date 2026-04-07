package com.kraftadmin.annotations

import kotlin.reflect.KClass

/**
 * Defines a relational link to another managed KraftAdmin resource.
 *
 * This annotation transforms a simple Foreign Key (ID) into a rich UI element.
 * Instead of requiring the admin to type a UUID, the engine will provide a
 * searchable autocomplete dropdown and render the record as a clickable link
 * in the data table.
 *
 * @property resource The name of the target resource to link to. This should
 * match the [KraftAdminResource.label] or class name of the target entity.
 * @property displayField The property on the target resource to be used as
 * the human-readable label (e.g., "fullName", "email", or "title").
 * @property lookupKey The property on the target resource that represents
 * the actual value to be stored (usually the Primary Key, e.g., "id").
 */
//@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Target(
    AnnotationTarget.FIELD,        // Java fields, Kotlin @field:
    AnnotationTarget.PROPERTY,     // Kotlin bare property annotation
    AnnotationTarget.FUNCTION,     // Kotlin @get: / Java getter
    AnnotationTarget.PROPERTY_GETTER // setter method lookup
)
@Retention(AnnotationRetention.RUNTIME)
annotation class KraftAdminLookup(
    val resource: KClass<*>,
    val displayField: String,
    val lookupKey: String = "id"
)

