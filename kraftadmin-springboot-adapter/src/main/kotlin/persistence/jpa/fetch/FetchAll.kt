package persistence.jpa.fetch

import api.utils.ResourceRow
import com.kraftadmin.annotations.KraftAdminResource
import com.kraftadmin.api.responses.PagedResponse
import com.kraftadmin.config.PaginationConfig
import com.kraftadmin.context.KraftAdminContextHolder
import com.kraftadmin.events.KraftAdminEvent
import com.kraftadmin.events.KraftLifecycleService
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.logging.KraftAdminLogging
import jakarta.persistence.EntityManager
import org.springframework.transaction.support.TransactionTemplate
import persistence.error.PersistenceErrorResolver
import persistence.error.PersistenceException
import persistence.jpa.mapper.ResourceRowMapper
import persistence.jpa.metadata.JpaEntityMetadata
import persistence.jpa.query.CriteriaQueryBuilder
import persistence.jpa.query.PageableBuilder
import persistence.jpa.query.PredicateBuilder
import persistence.jpa.query.SortBuilder
import kotlin.collections.emptyList
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Handles paginated entity fetching.
 * Delegates all query construction to the query/ package —
 * this class only owns the fetch lifecycle.
 *
 * Contract: this method either returns a valid PagedResponse or throws
 * PersistenceException. It never silently returns an empty page to mask
 * a failure — callers can rely on that distinction.
 */
class FetchAll<T : Any>(
    private val entityClass: KClass<T>,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val metadata: JpaEntityMetadata<T>,
    private val rowMapper: ResourceRowMapper,
    private val pagination: PaginationConfig,
    private val lifecycle: KraftLifecycleService,
    val errorResolver: PersistenceErrorResolver,
) {
    private val logger = KraftAdminLogging.logger(javaClass)


    fun execute(
        page: Int,
        size: Int,
        columns: List<KraftAdminColumn>,
        searchQuery: String? = null,
        sortField: String? = null,
        sortDirection: String? = null,
        filters: List<PredicateBuilder.Filter> = emptyList()
    ): PagedResponse<ResourceRow> {
        logger.info("fetching all resources for ${metadata.entityName} and query $searchQuery")

        val context = KraftAdminContextHolder.adminContext()


        // Check if the resource has search globally disabled via annotation
        val resourceAnno = entityClass.findAnnotation<KraftAdminResource>()
        val globalSearchEnabled = resourceAnno?.searchable ?: true

        val activeSearchQuery = if (globalSearchEnabled) searchQuery else null

        val pageSpec = PageableBuilder.PageSpec(
            page = page,
            size = size,
            maxSize = pagination.maxPageSize
        )

        val sortSpec = SortBuilder.from(
            field = sortField ?: metadata.defaultSort,
            direction = sortDirection
        )

        lifecycle.onBeforeFetchAll(
            KraftAdminEvent.BeforeFetchAll(
                resourceName = metadata.entityName,
                page = page,
                size = size,
                filters = PredicateBuilder.toEventFilters(filters),
                sortField = sortField,
                sortDirection = sortDirection,
                context = context,
                searchQuery = activeSearchQuery,
            )
        )

        val response = transactionTemplate.execute { status ->
            try {
                val queryBuilder = CriteriaQueryBuilder(entityManager, entityClass, metadata)
                    .where(filters)
                    .search(activeSearchQuery, if (globalSearchEnabled) metadata.searchableFields else emptyList())
                    .sort(
                        sortSpec,
                        allowedFields = metadata.sortableFields
                    )

                // Count before applying pagination — total matching records
                val total = queryBuilder.count()

                // Data query with pagination applied
                val entities = queryBuilder
                    .page(pageSpec)
                    .buildAndExecute()

                val tableColumns = columns.filter { it.showInTable }

                val rows = entities.map { entity ->
                    metadata.ensureLobsInitialized(entity)
                    rowMapper.mapToRow(entity, tableColumns)
                }

                val result = PagedResponse(
                    items = rows,
                    total = total,
                    page = pageSpec.effectivePage,
                    totalPages = PageableBuilder.totalPages(total, pageSpec.effectiveSize),
                    pageSize = pageSpec.effectiveSize
                )

                lifecycle.onAfterFetchAll(
                    KraftAdminEvent.AfterFetchAll(
                        resourceName = metadata.entityName,
                        page = result.page,
                        size = result.pageSize,
                        total = result.total,
                        returned = result.items.size,
                        context = context
                    )
                )

                result


            } catch (e: Exception) {
                logger.error("FetchAll failed for ${metadata.entityName}: ${e.message}", e)
                status.setRollbackOnly()
                lifecycle.onFetchAllFailed(
                    KraftAdminEvent.FetchAllFailed(
                        resourceName = metadata.entityName,
                        page = page,
                        size = size,
                        exception = e,
                        context = context,
                        searchQuery = activeSearchQuery,
                    )
                )

                throw PersistenceException(
                    errorResolver.resolve(metadata.entityName, e),
                    e
                )
            }
        }

        return response ?: throw PersistenceException(
            errorResolver.resolve(
                entityClass.simpleName ?: "Resource",
                IllegalStateException("Transaction completed without a result for ${metadata.entityName}")
            )
        )
    }
}