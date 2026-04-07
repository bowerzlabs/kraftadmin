package com.kraftadmin.spi

import com.kraftadmin.enums.FormInputType
import com.kraftadmin.ui_descriptors.ColumnDescriptor
import com.kraftadmin.ui_descriptors.LookupDescriptor
import kotlin.reflect.KClass

/**
 * Abstract base class for resources
 */
abstract class AbstractResource<T : Any>(
    override val name: String,
    override val label: String,
    override val entityClass: KClass<T>
) : KraftAdminResource<T> {
    override var dataProvider: KraftDataProvider<T>? = null

    private val _columns = mutableListOf<KraftAdminColumn>()

    override val columns: List<KraftAdminColumn>
        get() = _columns.toList()

    protected fun column(
        name: String,
        label: String = name.replaceFirstChar { it.uppercase() },
        type: FormInputType = FormInputType.TEXT,
        searchable: Boolean = false,
        sortable: Boolean = false,
        visible: Boolean = true,
        required: Boolean = false,
        defaultValue: Any? = null,
        selectOptions: List<SelectOption>? = null,
        subColumns: List<ColumnDescriptor>? = null,
        placeholder: String? = null,
        lookup: LookupDescriptor? = null,
        validationRules: String? = null,
        validationMessages: Map<String, String>? = null,
    ) {
        _columns.add(
            KraftAdminColumn(
                name = name,
                label = label,
                type = type,
                searchable = searchable,
                sortable = sortable,
                visible = visible,
                required = required,
                defaultValue = defaultValue,
                selectOptions = selectOptions,
                subColumns = subColumns,
                placeholder = placeholder,
                lookup = lookup,
                validationRules = validationRules,
                validationMessages = validationMessages,
            )
        )
    }

}