package com.kraftadmin.annotations

/**
 * Defines a reactive dependency between fields within the same resource.
 *
 * Use this to create dynamic, "smart" forms. The annotated field will be
 * hidden by default and will only become visible (and validated) when the
 * watched [field] matches the specified [value].
 *
 * **Example:**
 * ```kotlin
 * @KraftAdminField(inputType = FormInputType.CHECKBOX)
 * val isRemote: Boolean = false
 *
 * @KraftAdminDependsOn(field = "isRemote", value = "true")
 * val homeOfficeAllowance: Double = 0.0
 * ```
 *
 * @property field The name of the sibling property in this class to watch for changes.
 * @property value The string representation of the value required to trigger visibility.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class KraftAdminDependsOn(
    val field: String,
    val value: String
)