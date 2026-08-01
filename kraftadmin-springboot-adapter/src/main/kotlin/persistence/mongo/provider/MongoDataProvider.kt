package persistence.mongo.provider

import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.api.responses.PagedResponse
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.model.BulkDeleteOutcome
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.spi.KraftDataProvider
import com.kraftadmin.ui_descriptors.LookupDescriptor
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty
import org.springframework.data.mongodb.core.query.Query
import persistence.mongo.delete.DocumentDeleter
import persistence.mongo.fetch.FetchAll
import persistence.mongo.fetch.FetchById
import persistence.mongo.lookup.MongoLookupProvider
import persistence.mongo.mapper.MongoResourceRowMapper
import persistence.mongo.metadata.MongoEntityMetadata
import persistence.mongo.save.DocumentSaver
import kotlin.reflect.KClass

/**
 * All logic lives in
 * focused classes (FetchAll, FetchById, DocumentSaver, DocumentDeleter,
 * MongoLookupProvider).
 */
class MongoDataProvider<T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val entityClass: KClass<T>,
    applicationContext: ApplicationContext
) : KraftDataProvider<T> {

    private val logger = KraftAdminLogging.logger(javaClass)

    private val mappingContext: MongoMappingContext =
        mongoTemplate.converter.mappingContext as MongoMappingContext

    private val persistentEntity: MongoPersistentEntity<*> =
        mappingContext.getRequiredPersistentEntity(entityClass.java)

    private val idProperty: MongoPersistentProperty? =
        persistentEntity.idProperty

    private val rowMapper =
        MongoResourceRowMapper(entityClass = entityClass, applicationContext = applicationContext)

    private val entityMetadata = MongoEntityMetadata(entityClass)

    private val fetchAllExecutor = FetchAll(entityClass, mongoTemplate, rowMapper)
    private val fetchByIdExecutor = FetchById(
        entityClass, mongoTemplate, idProperty, rowMapper,
        entityMetadata
    )
    private val documentSaver = DocumentSaver(
        entityClass, mongoTemplate, persistentEntity, idProperty,
        entityMetadata
    )
    private val documentDeleter = DocumentDeleter(entityClass, mongoTemplate)
    private val lookupProvider = MongoLookupProvider(mongoTemplate, applicationContext)

    override fun fetchAll(
        page: Int,
        size: Int,
        query: String?,
        columns: List<KraftAdminColumn>,
        sortField: String?,
        sortDirection: String?
    ): PagedResponse<ResourceRow> =
        fetchAllExecutor.execute(page, size, columns, query, sortField, sortDirection)

    override fun fetchById(id: String, columns: List<KraftAdminColumn>): ResourceRow? =
        fetchByIdExecutor.execute(id, columns)

    override fun save(name: String, data: Map<String, Any?>): Map<String, Any?> {
        val rawId = data["id"] ?: data["_id"]
        val id = rawId?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "0" }

        val savedEntity = if (id != null) {
            logger.info("save() → UPDATE {} #{}", entityClass.simpleName, id)
            documentSaver.update(id, data)
        } else {
            logger.info("save() → CREATE {}", entityClass.simpleName)
            documentSaver.create(data)
        }

        if (savedEntity == null) return emptyMap()

        return try {
            rowMapper.mapEntityToData(savedEntity)
        } catch (e: Exception) {
            logger.warn("Could not map saved entity back to data map: ${e.message}")
            emptyMap()
        }
    }

    override fun delete(id: String): KraftOperationResponse<Unit> {
        val existing = fetchByIdExecutor.fetchEntity(id)
        return documentDeleter.delete(id, existing)
    }

    override fun getLookupData(
        lookup: LookupDescriptor,
        limit: Int,
        searchQuery: String?
    ): List<ObjectResponse> {
        logger.info("getLookupData(lookup: {}, limit: {}, searchQuery: {})", lookup, limit, searchQuery)
        return lookupProvider.lookup(lookup, searchQuery, limit)
    }

    override fun countAll(name: String): Long? {
        return try {
            mongoTemplate.count(Query(), entityClass.java)
        } catch (e: Exception) {
            logger.error("countAll failed for ${entityClass.simpleName}: ${e.message}", e)
            null
        }
    }

    override fun getLookupDataByIds(lookup: LookupDescriptor, ids: List<String>): List<ObjectResponse> {
        if (ids.isEmpty()) return emptyList()
        return lookupProvider.lookupByIds(lookup, ids)
    }

    override fun findById(id: String): T? = fetchByIdExecutor.fetchEntity(id)

    override fun bulkDelete(ids: List<String>) : BulkDeleteOutcome {
        TODO("Not yet implemented")
    }

}