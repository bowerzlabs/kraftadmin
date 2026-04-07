package com.kraftadmin.ui_descriptors

data class LookupDescriptor(
    val targetEntity: String?, // e.g., "Institution"
    val searchField: String?,   // e.g., "name" or "code"
    val lookupKey: String?,
)