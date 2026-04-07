package com.kraftadmin.annotations

import com.kraftadmin.enums.FormInputType

/**
 * Customizes the behavior, appearance, and validation of an entity property.
 * * Apply this to fields within a @KraftAdminResource class to override the
 * engine's default inference logic.
 *
 * @property label The display name for table headers and form labels.
 * @property inputType The UI component type used in forms. When [FormInputType.UNSET],
 * the engine infers the type from the property's JVM class.
 * @property showInTable Visibility toggle for the data table list view. Use 'false'
 * for long text fields or sensitive identifiers.
 * @property group Logical grouping of fields within the Edit/Create form (e.g., "Contact Info").
 * @property sortable Enables or disables UI-driven sorting for this specific column.
 * @property required Backend and Frontend validation flag. If true, the field cannot be null/empty.
 * @property regex A regular expression string used to validate input data on both
 * the client and the server.
 * @property sensitive If true, the value is masked in the UI and can be omitted
 * from bulk list fetches to protect PII.
 * @property placeholder The hint text displayed inside the input field when it is empty.
 * @property readonly If true, the field is visible in the form but its value
 * cannot be modified by the admin user.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class KraftAdminField(
    val label: String = "",
    val inputType: FormInputType = FormInputType.UNSET,
    val showInTable: Boolean = true,
    val group: String = "General",
    val sortable: Boolean = true,
    val required: Boolean = false,
    val regex: String = "",
    val validationMessage: String = "", // Custom error message for regex/required
    val sensitive: Boolean = false,
    val placeholder: String = "",
    val readonly: Boolean = false,
    val displayField: Boolean = false,
)
