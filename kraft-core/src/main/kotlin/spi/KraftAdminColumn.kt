package com.kraftadmin.spi

import com.kraftadmin.enums.FormInputType
import com.kraftadmin.ui_descriptors.ColumnDescriptor
import com.kraftadmin.ui_descriptors.LookupDescriptor
import kotlin.reflect.KClass

data class KraftAdminColumn(
    val name: String,
    val label: String,
    val type: FormInputType,
    val searchable: Boolean = false,
    val sortable: Boolean = false,
    val visible: Boolean = true,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val selectOptions: List<SelectOption>? = null,
    val subColumns: List<ColumnDescriptor>? = null,
    val placeholder: String? = null,
    val validationRules: String? = null,

    // Map of Rule -> Custom Message
    // e.g., "required" -> "Please enter your email"
    val validationMessages: Map<String, String>? = null,

    // Temporary container for server-side validation results
    var currentError: String? = null,
    // The configuration for the lookup
    val lookup: LookupDescriptor? = null
) {
    fun toDescriptor(): ColumnDescriptor =
        ColumnDescriptor(
            name = name,
            label = label,
            type = type.name,
            searchable = searchable,
            sortable = sortable,
            visible = visible,
            required = required,
            defaultValue = defaultValue,
            selectOptions = selectOptions,
            subColumns = subColumns,
            placeholder = placeholder,
            validationRules = validationRules,
            validationMessages = validationMessages,
            error = currentError,
            lookup = lookup
        )
}

data class SelectOption(
    val label: String,
    val value: String
)
