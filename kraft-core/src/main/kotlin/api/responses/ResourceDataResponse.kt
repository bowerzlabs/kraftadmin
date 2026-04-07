package api.responses

import api.utils.ResourceRow
import com.kraftadmin.ui_descriptors.ResourceDescriptor

data class ResourceDataResponse(
    val resource: ResourceDescriptor,
//    val rows: List<ResourceRow>
//    val pagedResponse: PagedResponse<ResourceRow>
)

data class PagedResponse<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

