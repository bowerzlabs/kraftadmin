package persistence.mongo.fetch

import api.utils.ResourceRow
import com.kraftadmin.api.responses.PagedResponse
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.query.KraftFilter
import com.kraftadmin.spi.KraftAdminColumn
import org.springframework.data.mongodb.core.MongoTemplate
import persistence.mongo.mapper.MongoResourceRowMapper
import persistence.mongo.query.MongoQueryBuilder
import persistence.mongo.query.PageableBuilder
import persistence.mongo.query.SortBuilder
import kotlin.reflect.KClass

class FetchAll<T : Any>(
    private val entityClass: KClass<T>,
    private val mongoTemplate: MongoTemplate,
    private val rowMapper: MongoResourceRowMapper
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    fun execute(
        page: Int,
        size: Int,
        columns: List<KraftAdminColumn>,
        searchQuery: String?,
        sortField: String?,
        sortDirection: String?,
        filters: List<KraftFilter> = emptyList()
    ): PagedResponse<ResourceRow> {

        val pageSpec = PageableBuilder.PageSpec(page = page, size = size)
        val sortSpec = SortBuilder.from(sortField, sortDirection)

        val searchableFields = columns.filter { it.searchable }.map { it.name }
        val sortableFields = columns.filter { it.sortable }.map { it.name }

        val queryBuilder = MongoQueryBuilder(mongoTemplate, entityClass)
            .where(filters)
            .search(searchQuery, searchableFields)
            .sort(sortSpec, sortableFields)

        val total = queryBuilder.count()

        val entities = queryBuilder
            .page(pageSpec)
            .buildAndExecute()

        val rows = entities.map { rowMapper.mapToRow(it, columns) }

        return PagedResponse(
            items = rows,
            total = total,
            page = pageSpec.effectivePage,
            pageSize = pageSpec.effectiveSize,
            totalPages = PageableBuilder.totalPages(total, pageSpec.effectiveSize)
        )
    }
}