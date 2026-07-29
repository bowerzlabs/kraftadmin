package persistence.jpa.provider

import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.api.responses.PagedResponse
import com.kraftadmin.config.PaginationConfig
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.ui_descriptors.LookupDescriptor
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.model.BulkDeleteOutcome
import com.kraftadmin.spi.KraftDataProvider
import config.KraftAdminProperties
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationContext
import org.springframework.transaction.support.TransactionTemplate
import persistence.jpa.conversion.TypeConverter
import persistence.jpa.delete.EntityDeleter
import persistence.jpa.fetch.FetchAll
import persistence.jpa.fetch.FetchById
import persistence.jpa.lookup.LookupProvider
import persistence.jpa.mapper.ResourceRowMapper
import persistence.jpa.save.EntityInstantiator
import persistence.jpa.save.EntitySaver
import persistence.jpa.save.PropertyWriter
import persistence.jpa.save.RelationshipWriter
import events.SpringKraftLifecycleService
import persistence.error.DefaultPersistenceErrorResolver
import persistence.jpa.bulk_actions.BulkDeleteResult
import persistence.jpa.metadata.JpaEntityMetadata
import persistence.jpa.validation.PersistenceValidationService
import security.SecurityProviderChain
import kotlin.reflect.KClass


/**
 * Thin orchestrator. All logic lives in focused collaborators.
 * Implements KraftDataProvider<T> exactly as defined in the interface.
 */
class JpaDataProvider<T : Any>(
    private val entityClass: KClass<T>,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val applicationContext: ApplicationContext,
    private val adminStorageProvider: AdminStorageProvider,
    private val securityChain: SecurityProviderChain,
    private val properties: KraftAdminProperties,
    paginationProperties: PaginationConfig,
    lifecycleService: SpringKraftLifecycleService,
    persistenceValidationService: PersistenceValidationService
) : KraftDataProvider<T> {

    private val logger = KraftAdminLogging.logger(javaClass)


    private val entityMetadata = JpaEntityMetadata(entityClass)
    private val rowMapper = ResourceRowMapper(entityClass, applicationContext)

    private val fetchAllExecutor = FetchAll(
        entityClass, entityManager, transactionTemplate,
        entityMetadata, rowMapper, paginationProperties, lifecycleService,
        errorResolver = DefaultPersistenceErrorResolver()
    )

    private val fetchByIdExecutor = FetchById(
        entityClass, entityManager, transactionTemplate,
        entityMetadata, rowMapper,
        lifecycle = lifecycleService,
        errorResolver = DefaultPersistenceErrorResolver()
    )

    private val entitySaver = EntitySaver(
        entityClass = entityClass,
        entityManager = entityManager,
        transactionTemplate = transactionTemplate,
        metadata = entityMetadata,
        instantiator = EntityInstantiator(entityClass),
        propertyWriter = PropertyWriter(TypeConverter),
        relationshipWriter = RelationshipWriter(entityManager),
        lifecycle = lifecycleService,
        errorResolver = DefaultPersistenceErrorResolver(),
        validationService = persistenceValidationService,
    )

    private val entityDeleter = EntityDeleter(
        entityClass, entityManager, transactionTemplate, entityMetadata, adminStorageProvider, lifecycleService,
        DefaultPersistenceErrorResolver()
    )

    private val lookupProvider = LookupProvider(entityManager, applicationContext)

    override fun fetchAll(
        page: Int,
        size: Int,
        query: String?,
        columns: List<KraftAdminColumn>,
        sortField: String?,
        sortDirection: String?
    ): PagedResponse<ResourceRow> = fetchAllExecutor.execute(page, size,  columns, query, sortField, sortDirection)

    override fun fetchById(
        id: String,
        columns: List<KraftAdminColumn>
    ): ResourceRow? = fetchByIdExecutor.execute(id, columns)

    /**
     * Unified save — determines CREATE vs UPDATE from whether `id`
     * is present and non-blank in [data].
     *
     * Returns a map of the saved entity's values so the UI can
     * reflect the server-assigned fields (e.g. generated ID, timestamps).
     */
    override fun save(name: String, data: Map<String, Any?>): Map<String, Any?> {
        logger.info("provider {}, data {}", name, data)

        val rawId = data["id"]

        val id = when (rawId) {
            null -> null
            is Number -> rawId.toLong().takeIf { it > 0 }?.toString()
            else -> rawId.toString()
                .trim()
                .takeIf { it.isNotBlank() && it != "0" }
        }

        val savedEntity = if (id != null) {
            logger.info("save() → UPDATE {} #{}", entityClass.simpleName, id)
            entitySaver.update(id, data)
        } else {
            logger.info("save() → CREATE {}", entityClass.simpleName)
            entitySaver.create(data)
        }

        if (savedEntity == null) {
            return emptyMap()
        }

        return try {
            rowMapper.mapEntityToData(savedEntity)
        } catch (e: Exception) {
            logger.warn("Could not map saved entity back to data map: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Deletes the entity with the given [id]. No-ops silently if not found
     * (idempotent — consistent with REST DELETE semantics).
     */
    override fun delete(id: String): KraftOperationResponse<Unit> {
        return entityDeleter.delete(id)
    }

    /**
     * Resolves lookup options from a [LookupDescriptor].
     * Used by relation fields to power typeahead search in the UI.
     */
    override fun getLookupData(
        lookup: LookupDescriptor,
        limit: Int,
        searchQuery: String?
    ): List<ObjectResponse> {
        return lookupProvider.lookup(
            lookup = lookup,
            searchQuery = searchQuery,
            limit = limit,
//            displayField = ""
        )
    }

    /**
     * Total count of all (non-deleted) records for the given resource [name].
     * Used by the dashboard and table headers.
     * [name] is ignored here — this provider is already scoped to [entityClass].
     */
    override fun countAll(name: String): Long? {
        return try {
            transactionTemplate.execute {
                val cb = entityManager.criteriaBuilder
                val cq = cb.createQuery(Long::class.java)
                cq.select(cb.count(cq.from(entityClass.java)))
                entityManager.createQuery(cq).singleResult
            }
        } catch (e: Exception) {
            logger.error("countAll failed for ${entityClass.simpleName}: ${e.message}", e)
            null
        }
    }

    override fun getLookupDataByIds(
        lookup: LookupDescriptor,
        ids: List<String>
    ): List<ObjectResponse> {
        if (ids.isEmpty()) return emptyList()
        return lookupProvider.lookupByIds(lookup, ids)
    }

    override fun findById(id: String) : T? {
        return fetchByIdExecutor.fetchEntity(id)
    }

    override fun bulkDelete(ids: List<String>): BulkDeleteOutcome {
        return entityDeleter.bulkDelete(ids)
    }


}
