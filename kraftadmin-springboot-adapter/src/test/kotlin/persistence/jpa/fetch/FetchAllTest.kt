package persistence.jpa.fetch

import api.utils.ResourceRow
import com.kraftadmin.annotations.KraftAdminResource
import com.kraftadmin.api.responses.PagedResponse
import com.kraftadmin.config.PaginationConfig
import com.kraftadmin.context.KraftAdminContext
import com.kraftadmin.context.KraftAdminContextHolder
import com.kraftadmin.events.KraftAdminEvent
import com.kraftadmin.events.KraftLifecycleService
import com.kraftadmin.spi.KraftAdminColumn
import jakarta.persistence.EntityManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import persistence.error.PersistenceErrorDetails
import persistence.error.PersistenceErrorResolver
import persistence.error.PersistenceException
import persistence.jpa.mapper.ResourceRowMapper
import persistence.jpa.metadata.JpaEntityMetadata
import persistence.jpa.query.CriteriaQueryBuilder
import persistence.jpa.query.PageableBuilder
import persistence.jpa.query.PredicateBuilder
import persistence.jpa.query.SortBuilder

/**
 * Unit tests for [FetchAll].
 */
class FetchAllTest {

    private class SearchableEntity
    // ASSUMPTION: `searchable` is the only required annotation parameter.
    @KraftAdminResource(searchable = false)
    private class NonSearchableEntity

    private lateinit var entityManager: EntityManager
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var metadata: JpaEntityMetadata<SearchableEntity>
    private lateinit var rowMapper: ResourceRowMapper
    private lateinit var pagination: PaginationConfig
    private lateinit var lifecycle: KraftLifecycleService
    private lateinit var errorResolver: PersistenceErrorResolver
    private lateinit var txStatus: TransactionStatus
    private lateinit var adminContext: KraftAdminContext

    @BeforeEach
    fun setUp() {
        entityManager = mockk(relaxed = true)
        transactionTemplate = mockk()
        metadata = mockk(relaxed = true)
        rowMapper = mockk(relaxed = true)
        pagination = mockk(relaxed = true)
        lifecycle = mockk(relaxed = true)
        errorResolver = mockk(relaxed = true)
        txStatus = mockk(relaxed = true)
        adminContext = mockk(relaxed = true)

        every { metadata.entityName } returns "widgets"
        every { metadata.defaultSort } returns "id"
        every { metadata.sortableFields } returns listOf("id", "name")
        every { metadata.searchableFields } returns listOf("name", "description")
        every { pagination.maxPageSize } returns 100

        mockkObject(KraftAdminContextHolder)
        every { KraftAdminContextHolder.adminContext() } returns adminContext

        // Every FetchAll.execute() call constructs exactly one CriteriaQueryBuilder;
        // intercept that construction so we never touch a real EntityManager/DB.
        mockkConstructor(CriteriaQueryBuilder::class)
        every { anyConstructed<CriteriaQueryBuilder<*>>().where(any()) } answers { self as CriteriaQueryBuilder<*> }
        every { anyConstructed<CriteriaQueryBuilder<*>>().search(any(), any()) } answers { self as CriteriaQueryBuilder<*> }
        every { anyConstructed<CriteriaQueryBuilder<*>>().sort(any(), any()) } answers { self as CriteriaQueryBuilder<*> }
        every { anyConstructed<CriteriaQueryBuilder<*>>().page(any()) } answers { self as CriteriaQueryBuilder<*> }
        every { anyConstructed<CriteriaQueryBuilder<*>>().count() } returns 0L
        every { anyConstructed<CriteriaQueryBuilder<*>>().buildAndExecute() } returns emptyList<SearchableEntity>()

        // Run whatever lambda is handed to transactionTemplate.execute { ... }
        // against our mock TransactionStatus, and return its result.
        every {
            transactionTemplate.execute(any<TransactionCallback<PagedResponse<ResourceRow>>>())
        } answers {
            firstArg<TransactionCallback<PagedResponse<ResourceRow>>>().doInTransaction(txStatus)
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // Helpers

    private fun fetchAllFor(entityClass: kotlin.reflect.KClass<SearchableEntity> = SearchableEntity::class) =
        FetchAll(
            entityClass = entityClass,
            entityManager = entityManager,
            transactionTemplate = transactionTemplate,
            metadata = metadata,
            rowMapper = rowMapper,
            pagination = pagination,
            lifecycle = lifecycle,
            errorResolver = errorResolver
        )

    private fun column(showInTable: Boolean): KraftAdminColumn {
        val col = mockk<KraftAdminColumn>(relaxed = true)
        every { col.showInTable } returns showInTable
        return col
    }

    @Test
    fun `maps entities through the row mapper using only table-visible columns, and builds the paged response`() {
        val visibleColumn = column(showInTable = true)
        val hiddenColumn = column(showInTable = false)
        val otherVisibleColumn = column(showInTable = true)

        val entity1 = SearchableEntity()
        val entity2 = SearchableEntity()
        every { anyConstructed<CriteriaQueryBuilder<*>>().buildAndExecute() } returns listOf(entity1, entity2)
        every { anyConstructed<CriteriaQueryBuilder<*>>().count() } returns 7L

        val row1 = mockk<ResourceRow>()
        val row2 = mockk<ResourceRow>()

        val tableColumnsSlot = slot<List<KraftAdminColumn>>()
        every {
            rowMapper.mapToRow(entity1, capture(tableColumnsSlot))
        } returns row1
        every {
            rowMapper.mapToRow(eq(entity2), any())
        } returns row2

        val fetchAll = fetchAllFor()

        val response = fetchAll.execute(
            page = 2,
            size = 10,
            columns = listOf(visibleColumn, hiddenColumn, otherVisibleColumn)
        )

        // Only the two showInTable=true columns are forwarded.
        assertEquals(listOf(visibleColumn, otherVisibleColumn), tableColumnsSlot.captured)

        // Lobs initialized for every returned entity, before mapping.
        verify { metadata.ensureLobsInitialized(entity1) }
        verify { metadata.ensureLobsInitialized(entity2) }

        assertEquals(listOf(row1, row2), response.items)
        assertEquals(7L, response.total)

        // Derive expected page/size/totalPages the same way FetchAll does,
        // without hardcoding PageableBuilder's internal formula.
        val expectedPageSpec = PageableBuilder.PageSpec(page = 2, size = 10, maxSize = pagination.maxPageSize)
        assertEquals(expectedPageSpec.effectivePage, response.page)
        assertEquals(expectedPageSpec.effectiveSize, response.pageSize)
        assertEquals(PageableBuilder.totalPages(7L, expectedPageSpec.effectiveSize), response.totalPages)
    }

    @Test
    fun `fires onBeforeFetchAll then onAfterFetchAll with matching data, and never fires onFetchAllFailed`() {
        every { anyConstructed<CriteriaQueryBuilder<*>>().count() } returns 3L
        every { anyConstructed<CriteriaQueryBuilder<*>>().buildAndExecute() } returns
                listOf(SearchableEntity(), SearchableEntity(), SearchableEntity())
        every { rowMapper.mapToRow(any(), any()) } returns mockk(relaxed = true)

        val fetchAll = fetchAllFor()

        fetchAll.execute(
            page = 1,
            size = 20,
            columns = emptyList(),
            searchQuery = "widget",
            sortField = "name",
            sortDirection = "asc"
        )

        val beforeSlot = slot<KraftAdminEvent.BeforeFetchAll>()
        verify(exactly = 1) { lifecycle.onBeforeFetchAll(capture(beforeSlot)) }
        assertEquals("widgets", beforeSlot.captured.resourceName)
        assertEquals(1, beforeSlot.captured.page)
        assertEquals(20, beforeSlot.captured.size)
        assertEquals("name", beforeSlot.captured.sortField)
        assertEquals("asc", beforeSlot.captured.sortDirection)
        assertEquals("widget", beforeSlot.captured.searchQuery)
        assertSame(adminContext, beforeSlot.captured.context)

        val afterSlot = slot<KraftAdminEvent.AfterFetchAll>()
        verify(exactly = 1) { lifecycle.onAfterFetchAll(capture(afterSlot)) }
        assertEquals("widgets", afterSlot.captured.resourceName)
        assertEquals(3L, afterSlot.captured.total)
        assertEquals(3, afterSlot.captured.returned)
        assertSame(adminContext, afterSlot.captured.context)

        verify(exactly = 0) { lifecycle.onFetchAllFailed(any()) }
    }

    // Search enable/disable via @KraftAdminResource
    @Test
    fun `defaults to searchable = true when no @KraftAdminResource annotation is present`() {
        val fetchAll = fetchAllFor(SearchableEntity::class)

        fetchAll.execute(page = 1, size = 20, columns = emptyList(), searchQuery = "widget")

        verify {
            anyConstructed<CriteriaQueryBuilder<*>>().search("widget", metadata.searchableFields)
        }
        val beforeSlot = slot<KraftAdminEvent.BeforeFetchAll>()
        verify { lifecycle.onBeforeFetchAll(capture(beforeSlot)) }
        assertEquals("widget", beforeSlot.captured.searchQuery)
    }

    @Test
    fun `disables search entirely when @KraftAdminResource(searchable = false) is present, regardless of the query passed in`() {
        @Suppress("UNCHECKED_CAST")
        val fetchAll = FetchAll(
            entityClass = NonSearchableEntity::class,
            entityManager = entityManager,
            transactionTemplate = transactionTemplate,
            metadata = metadata as JpaEntityMetadata<NonSearchableEntity>,
            rowMapper = rowMapper,
            pagination = pagination,
            lifecycle = lifecycle,
            errorResolver = errorResolver
        )

        fetchAll.execute(page = 1, size = 20, columns = emptyList(), searchQuery = "widget")

        verify {
            anyConstructed<CriteriaQueryBuilder<*>>().search(null, emptyList())
        }
        val beforeSlot = slot<KraftAdminEvent.BeforeFetchAll>()
        verify { lifecycle.onBeforeFetchAll(capture(beforeSlot)) }
        assertNull(beforeSlot.captured.searchQuery)
    }

    // Sorting
    @Test
    fun `falls back to the metadata default sort field when none is supplied`() {
        val sortSpecSlot = slot<SortBuilder.SortSpec>()
        every {
            anyConstructed<CriteriaQueryBuilder<*>>().sort(capture(sortSpecSlot), any())
        } answers { self as CriteriaQueryBuilder<*> }

        val fetchAll = fetchAllFor()
        fetchAll.execute(page = 1, size = 20, columns = emptyList(), sortField = null, sortDirection = "desc")

        val expectedSortSpec = SortBuilder.from(field = "id", direction = "desc")
        assertEquals(expectedSortSpec, sortSpecSlot.captured)
    }

    @Test
    fun `uses the caller-supplied sort field over the metadata default`() {
        val sortSpecSlot = slot<SortBuilder.SortSpec>()
        every {
            anyConstructed<CriteriaQueryBuilder<*>>().sort(capture(sortSpecSlot), any())
        } answers { self as CriteriaQueryBuilder<*> }

        val fetchAll = fetchAllFor()
        fetchAll.execute(page = 1, size = 20, columns = emptyList(), sortField = "createdAt", sortDirection = "desc")

        val expectedSortSpec = SortBuilder.from(field = "createdAt", direction = "desc")
        assertEquals(expectedSortSpec, sortSpecSlot.captured)
    }

    // Filters
    @Test
    fun `passes filters through to the query builder and translates them for the before-fetch event`() {
        val filters = listOf(mockk<PredicateBuilder.Filter>(), mockk<PredicateBuilder.Filter>())

        val fetchAll = fetchAllFor()
        fetchAll.execute(page = 1, size = 20, columns = emptyList(), filters = filters)

        verify { anyConstructed<CriteriaQueryBuilder<*>>().where(filters) }

        val beforeSlot = slot<KraftAdminEvent.BeforeFetchAll>()
        verify { lifecycle.onBeforeFetchAll(capture(beforeSlot)) }
        assertEquals(PredicateBuilder.toEventFilters(filters), beforeSlot.captured.filters)
    }

    // Failure handling
    @Test
    fun `wraps query failures in a PersistenceException, rolls back the transaction, and emits a failure event instead of onAfterFetchAll`() {
        val originalException = RuntimeException("query blew up")
        every { anyConstructed<CriteriaQueryBuilder<*>>().buildAndExecute() } throws originalException

        val resolvedInfo = mockk<PersistenceErrorDetails>(relaxed = true)
        every { errorResolver.resolve("widgets", originalException) } returns resolvedInfo

        val fetchAll = fetchAllFor()

        val thrown = assertThrows<PersistenceException> {
            fetchAll.execute(page = 1, size = 20, columns = emptyList(), searchQuery = "widget")
        }

        assertSame(originalException, thrown.cause)

        verify { txStatus.setRollbackOnly() }

        val failedSlot = slot<KraftAdminEvent.FetchAllFailed>()
        verify(exactly = 1) { lifecycle.onFetchAllFailed(capture(failedSlot)) }
        assertEquals("widgets", failedSlot.captured.resourceName)
        assertEquals(1, failedSlot.captured.page)
        assertEquals(20, failedSlot.captured.size)
        assertSame(originalException, failedSlot.captured.exception)
        assertEquals("widget", failedSlot.captured.searchQuery)
        assertSame(adminContext, failedSlot.captured.context)

        verify(exactly = 0) { lifecycle.onAfterFetchAll(any()) }
    }

    @Test
    fun `throws a PersistenceException when the transaction completes without a result`() {
        every {
            transactionTemplate.execute(any<TransactionCallback<PagedResponse<ResourceRow>>>())
        } returns null

        val resolvedInfo = mockk<PersistenceErrorDetails>(relaxed = true)
        every {
            errorResolver.resolve(any(), any<IllegalStateException>())
        } returns resolvedInfo

        val fetchAll = fetchAllFor()

        val thrown = assertThrows<PersistenceException> {
            fetchAll.execute(page = 1, size = 20, columns = emptyList())
        }

        assertTrue(thrown.cause is IllegalStateException)
        verify { errorResolver.resolve(any(), any<IllegalStateException>()) }
    }
}