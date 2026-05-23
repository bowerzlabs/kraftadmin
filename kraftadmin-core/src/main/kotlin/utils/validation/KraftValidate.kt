package com.kraftadmin.utils.validation

import kotlin.reflect.KClass

interface KraftValidate {
    /**
     * Scans the entire class and returns a map of:
     * Field Name -> Validation Rule String (e.g., "required|email")
     */
    fun extractValidationSchema(clazz: KClass<*>): Map<String, String>

    /**
     * Validates a map of raw input against a map of rules.
     * Returns a map of: Field Name -> Error Message
     */
    fun validate(
        rules: Map<String, String>,
        payload: Map<String, Any?>,
        labels: Map<String, String>
    ): Map<String, String>
}



