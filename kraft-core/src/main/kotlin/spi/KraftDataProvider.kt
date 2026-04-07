package com.kraftadmin.spi

import api.responses.PagedResponse
import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.ui_descriptors.LookupDescriptor
import kotlin.reflect.KClass

interface KraftDataProvider<T : Any> {
    fun fetchAll(page: Int, size: Int, columns: List<KraftAdminColumn>): PagedResponse<ResourceRow>
    fun fetchById(id: String, columns: List<KraftAdminColumn>): ResourceRow?
    fun save(name: String, data: Map<String, Any?>): Map<String, Any?>
    fun delete(id: String)
    fun getLookupData(lookup: LookupDescriptor, limit: Int = 20, searchQuery: String?): List<ObjectResponse>
    fun countAll(name: String): Long?
}