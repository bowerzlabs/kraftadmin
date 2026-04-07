package com.kraftadmin.utils.validation

interface KraftValidationExtractor {
    /**
     * Scans field/property and returns the DSL string (e.g. "required|email")
     */
    fun extractRules(field: Any): String

    /**
     * Maps specific rules to their custom messages
     */
    fun extractMessages(field: Any): Map<String, String>

    /**
     * Validates a value against the extracted rules for a specific field.
     * Returns a list of error messages, or empty if valid.
     */
    fun validate(field: Any?, value: Any?): List<String>
}

