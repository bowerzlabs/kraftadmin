package com.kraftadmin.ui_descriptors

import api.responses.PagedResponse
import api.utils.ResourceRow

data class ResourceDescriptor(
    val name: String,
    val label: String,
    val totalCount: Long = 0,
    val customActions: List<KraftActionDescriptor> = emptyList(),
    val columns: List<ColumnDescriptor>,
    val data: PagedResponse<ResourceRow> = PagedResponse(emptyList(), 0, 0, 20, 0)
)
