package com.kraftadmin.spi

import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.api.responses.PagedResponse
import com.kraftadmin.model.BulkDeleteOutcome
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.ui_descriptors.LookupDescriptor

interface KraftDataProvider<T : Any> {
    fun fetchAll(
        page: Int,
        size: Int,
        query: String?,
        columns: List<KraftAdminColumn>,
        sortField: String?,
        sortDirection: String?
    ): PagedResponse<ResourceRow>
    fun fetchById(id: String, columns: List<KraftAdminColumn>): ResourceRow?
    fun save(name: String, data: Map<String, Any?>): Map<String, Any?>
    fun delete(id: String): KraftOperationResponse<Unit>
    fun getLookupData(lookup: LookupDescriptor, limit: Int = 20, searchQuery: String?): List<ObjectResponse>
    fun countAll(name: String): Long?
    fun getLookupDataByIds(lookup: LookupDescriptor, ids: List<String>): List<ObjectResponse>
    fun findById(id: String): T?
    fun bulkDelete(ids: List<String>): BulkDeleteOutcome
}