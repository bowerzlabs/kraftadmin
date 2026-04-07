package com.kraftadmin.utils.validation

data class ValidationResponse(
    val success: Boolean,
    val data: Map<String, Any?>? = null,
    val errors: Map<String, List<String>> = emptyMap()
)