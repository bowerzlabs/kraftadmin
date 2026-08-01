package com.kraftadmin.ui_descriptors

data class LookupDescriptor(
    val targetEntity: String?,
    var lookupKey: String = "id",
    var displayField: String="id",
    val searchableFields: List<String> = emptyList()
)
