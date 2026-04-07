package com.kraftadmin.spi

import api.responses.PagedResponse
import api.utils.ResourceRow
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.enums.FormInputType.*
import com.kraftadmin.ui_descriptors.KraftActionDescriptor
import com.kraftadmin.ui_descriptors.ResourceDescriptor
import com.kraftadmin.utils.custom_actions.KraftActionResponse
import config.KraftAdminPropertiesConfig
import kotlin.reflect.KClass

interface KraftAdminResource<T : Any> {
    val name: String
    val label: String
    val customActions: List<KraftActionDescriptor>
    val entityClass: KClass<T>
    val columns: List<KraftAdminColumn>
    /**
     * Searchable columns are usually just strings or text-based fields.
     */
    val searchableColumns: List<String>
        get() = columns.filter { col ->
            col.searchable && listOf(TEXT, TEXTAREA, EMAIL, URL).contains(col.type)
        }.map { it.name }

    /**
     * Sortable columns are restricted to comparable types: numbers, dates, and IDs.
     */
    val sortableColumns: List<String>
        get() = columns.filter { col ->
            col.sortable && listOf(NUMBER, DATE, DATETIME, TIME, EMAIL, TEXT).contains(col.type)
        }.map { it.name }

    fun getIdentifier(entity: T): Any

    var dataProvider: KraftDataProvider<T>?

    /**
     * Fetch all rows for this resource
     * Returns a list of maps where key = column name, value = field value
     */
    fun getAllRows(page:Int, size: Int, columns: List<KraftAdminColumn>): PagedResponse<ResourceRow> = dataProvider?.fetchAll(page, size, columns) ?: PagedResponse(emptyList(), 0, 0, 0, 0)

    fun getById(id: String) = dataProvider?.fetchById(id, columns)

    fun save(name: String, data: Map<String, Any?>) = dataProvider?.save(name = name, data = data)

    fun delete(id: String) = dataProvider?.delete(id)

    fun countAll(name: String): Long? = dataProvider?.countAll(name)

    fun toDescriptor(): ResourceDescriptor =
        ResourceDescriptor(
            name = name,
            label = label,
            totalCount = countAll(name) ?: 0L,
            customActions = customActions.toList(),
            columns = columns.map { it.toDescriptor() },
            data = PagedResponse(emptyList(), 0, 0, 20, 0)
        )
}
