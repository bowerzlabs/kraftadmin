package persistence.mongo.lookup

import api.utils.ObjectResponse
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.ui_descriptors.LookupDescriptor
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import kotlin.reflect.KClass

/**
 * Resolves Mongo document classes for lookup operations and delegates
 * query execution to MongoLookupQuery.
 *
 * LookupDescriptor.targetEntity contains the target document's simple
 * class name, for example:
 *
 * JournalEntry
 * User
 * ProductCategory
 */
class MongoLookupProvider(
    private val mongoTemplate: MongoTemplate,
    private val applicationContext: ApplicationContext
) {

    private val logger =
        KraftAdminLogging.logger(javaClass)

    private val query =
        MongoLookupQuery(mongoTemplate)

    /**
     * Search-based lookup.
     *
     * Called while the user types into a RELATION or MULTI_RELATION
     * field.
     */
    fun lookup(
        lookup: LookupDescriptor,
        searchQuery: String?,
        limit: Int = 20
    ): List<ObjectResponse> {

        logger.info(
            "Mongo lookup requested: target={}, query={}",
            lookup.targetEntity,
            searchQuery
        )

        val targetEntity =
            lookup.targetEntity
                ?: run {
                    logger.warn(
                        "MongoLookupProvider: targetEntity is null"
                    )

                    return emptyList()
                }

        val entityClass =
            resolveDocumentClass(targetEntity)
                ?: run {
                    logger.warn(
                        "MongoLookupProvider: could not resolve " +
                                "Mongo document class for '{}'",
                        targetEntity
                    )

                    return emptyList()
                }

        return query.execute(
            entityClass = entityClass,
            lookup = lookup,
            query = searchQuery.orEmpty(),
            limit = limit
        )
    }

    /**
     * Resolves labels for existing relation IDs.
     *
     * Called when an edit form is opened. For example:
     *
     * Stored IDs:
     * [
     *     "68765e...",
     *     "68765f..."
     * ]
     *
     * Returned values:
     * [
     *     ObjectResponse(
     *         id = "68765e...",
     *         label = "My journal entry"
     *     )
     * ]
     */
    fun lookupByIds(
        lookup: LookupDescriptor,
        ids: List<String>
    ): List<ObjectResponse> {

        if (ids.isEmpty()) {
            return emptyList()
        }

        val targetEntity =
            lookup.targetEntity
                ?: run {
                    logger.warn(
                        "Mongo lookupByIds: targetEntity is null"
                    )

                    return emptyList()
                }

        val entityClass =
            resolveDocumentClass(targetEntity)
                ?: run {
                    logger.warn(
                        "MongoLookupProvider: could not resolve " +
                                "Mongo document class for '{}'",
                        targetEntity
                    )

                    return emptyList()
                }

        return query.executeByIds(
            entityClass = entityClass,
            lookup = lookup,
            ids = ids
        )
    }

    /**
     * Resolves a Mongo document using the Spring application context.
     *
     * MongoTemplate exposes persistent entity metadata, so we do not
     * need to scan packages manually.
     *
     * Matches:
     *
     * - Java/Kotlin simple class name
     * - @Document collection name
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveDocumentClass(
        entityName: String
    ): KClass<Any>? {

        return try {

            val mappingContext =
                mongoTemplate.converter
                    .mappingContext

            mappingContext
                .persistentEntities
                .firstOrNull { persistentEntity ->

                    val type =
                        persistentEntity.type

                    val simpleName =
                        type.simpleName

                    val documentAnnotation =
                        type.getAnnotation(
                            Document::class.java
                        )

                    val collectionName =
                        documentAnnotation
                            ?.collection
                            ?.takeIf {
                                it.isNotBlank()
                            }

                    simpleName.equals(
                        entityName,
                        ignoreCase = true
                    ) ||

                            collectionName?.equals(
                                entityName,
                                ignoreCase = true
                            ) == true
                }
                ?.type
                ?.kotlin as? KClass<Any>

        } catch (e: Exception) {

            logger.error(
                "Mongo document resolution failed for '{}': {}",
                e
            )

            null
        }
    }
}