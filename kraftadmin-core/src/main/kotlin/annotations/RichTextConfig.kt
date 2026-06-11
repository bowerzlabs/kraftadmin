package com.kraftadmin.annotations

/**
 * Nested configuration specifically targeting WYSIWYG input fields.
 */
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
annotation class RichTextConfig(
    val toolbarProfile: String = "standard", // "minimal", "standard", "full"
    val placeholder: String = "" // If empty, defaults to KraftAdminField.placeholder
)