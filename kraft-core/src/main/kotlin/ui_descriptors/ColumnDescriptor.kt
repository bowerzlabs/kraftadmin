package com.kraftadmin.ui_descriptors

import com.kraftadmin.spi.SelectOption
import kotlin.reflect.KClass

data class ColumnDescriptor(
    val name: String,
    val label: String,
    val type: String,
    val searchable: Boolean,
    val sortable: Boolean,
    val visible: Boolean,
    val required: Boolean,
    val defaultValue: Any? = null,
    val subColumns: List<ColumnDescriptor>? = null,
    val selectOptions: List<SelectOption>? = null,
    val placeholder: String? = null,
    val validationRules: String? = null,
    val validationMessages: Map<String, String>? = null,
    // Server-side error message (e.g., "This email is already taken")
    val error: String? = null,
    val lookup: LookupDescriptor? = null
)