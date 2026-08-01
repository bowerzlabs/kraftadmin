package persistence.mongo.lookup

import api.utils.ObjectResponse
import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.ui_descriptors.LookupDescriptor
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

/**
 * Executes generic Mongo lookup queries.
 *
 * Supports:
 *
 * - Search across LookupDescriptor.searchableFields
 * - Mongo ObjectId identifiers
 * - String identifiers
 * - UUID identifiers
 * - Numeric identifiers
 * - Existing relation label resolution
 */
class MongoLookupQuery(
    private val mongoTemplate: MongoTemplate
) {

    private val logger =
        KraftAdminLogging.logger(javaClass)

    private val labelCandidates =
        listOf(
            "name",
            "title",
            "label",
            "email",
            "username",
            "code",
            "fullName",
            "displayName"
        )

    /**
     * Search-based lookup.
     *
     * Uses case-insensitive regex matching across all searchable
     * fields defined by MongoLookupResolver.
     */
    fun <T : Any> execute(
        entityClass: KClass<T>,
        lookup: LookupDescriptor,
        query: String,
        limit: Int = 20
    ): List<ObjectResponse> {

        return try {

            val mongoQuery =
                Query()

            if (query.isNotBlank()) {

                val searchableFields =
                    lookup.searchableFields
                        .filter {
                            it.isNotBlank()
                        }

                if (searchableFields.isNotEmpty()) {

                    val escapedQuery =
                        Regex.escape(query)

                    val criteria =
                        searchableFields.map {
                            Criteria
                                .where(it)
                                .regex(
                                    escapedQuery,
                                    "i"
                                )
                        }

                    mongoQuery.addCriteria(
                        Criteria().orOperator(
                            *criteria.toTypedArray()
                        )
                    )
                }
            }

            mongoQuery.limit(
                limit.coerceIn(
                    1,
                    100
                )
            )

            mongoTemplate
                .find(
                    mongoQuery,
                    entityClass.java
                )
                .mapNotNull {
                        entity ->
                    toObjectResponse(
                        entity = entity,
                        preferredDisplayField =
                            lookup.displayField
                    )
                }

        } catch (e: Exception) {

            logger.error(
                "Mongo lookup failed for {}: {}",
                e
            )

            emptyList()
        }
    }

    /**
     * ID-based lookup.
     *
     * Used when opening an edit form so existing Mongo references
     * can be rendered using labels instead of raw IDs.
     */
    fun <T : Any> executeByIds(
        entityClass: KClass<T>,
        lookup: LookupDescriptor,
        ids: List<String>
    ): List<ObjectResponse> {

        if (ids.isEmpty()) {
            return emptyList()
        }

        return try {

            val idProperty =
                findIdProperty(entityClass)
                    ?: run {

                        logger.warn(
                            "No Mongo ID property found on {}",
                            entityClass.simpleName
                        )

                        return emptyList()
                    }

            val idFieldName =
                idProperty.name

            val idType =
                idProperty.returnType
                    .classifier as? KClass<*>

            val convertedIds =
                ids.mapNotNull {
                        id ->
                    coerceId(
                        value = id,
                        idType = idType
                    )
                }

            if (convertedIds.isEmpty()) {
                return emptyList()
            }

            val mongoQuery =
                Query(
                    Criteria
                        .where(idFieldName)
                        .`in`(convertedIds)
                )

            mongoTemplate
                .find(
                    mongoQuery,
                    entityClass.java
                )
                .mapNotNull {
                        entity ->
                    toObjectResponse(
                        entity = entity,
                        preferredDisplayField =
                            lookup.displayField
                    )
                }

        } catch (e: Exception) {

            logger.error(
                "Mongo lookupByIds failed for {}: {}",
                e
            )

            emptyList()
        }
    }

    /**
     * Finds a Mongo identifier.
     *
     * Priority:
     *
     * 1. Spring Data @Id
     * 2. id
     * 3. _id
     */
    private fun <T : Any> findIdProperty(
        entityClass: KClass<T>
    ): KProperty1<out T, *>? {

        val properties =
            entityClass.memberProperties

        return properties.firstOrNull {
                property ->

            property.findAnnotation<Id>() != null ||

                    property.javaField
                        ?.isAnnotationPresent(
                            Id::class.java
                        ) == true
        }
            ?: properties.firstOrNull {
                    property ->

                property.name.equals(
                    "id",
                    ignoreCase = true
                ) ||

                        property.name == "_id"
            }
    }

    /**
     * Converts frontend string IDs to the Mongo document's actual
     * identifier type.
     */
    private fun coerceId(
        value: String,
        idType: KClass<*>?
    ): Any? {

        return try {

            when (idType) {

                ObjectId::class ->

                    if (
                        ObjectId.isValid(value)
                    ) {
                        ObjectId(value)
                    } else {
                        null
                    }

                UUID::class ->

                    UUID.fromString(value)

                Long::class ->

                    value.toLong()

                Int::class ->

                    value.toInt()

                String::class,
                null ->

                    value

                else ->

                    value
            }

        } catch (e: Exception) {

            logger.debug(
                "Could not convert lookup ID '{}' to {}",
                value,
                idType?.simpleName
            )

            null
        }
    }

    /**
     * Converts a Mongo document into the canonical lookup response.
     */
    private fun toObjectResponse(
        entity: Any,
        preferredDisplayField: String?
    ): ObjectResponse? {

        val id =
            extractId(entity)
                ?.toString()
                ?: return null

        val properties =
            entity::class
                .memberProperties
                .associateBy {
                    it.name
                }

        fun value(
            fieldName: String?
        ): String? {

            if (
                fieldName.isNullOrBlank()
            ) {
                return null
            }

            val property =
                properties[fieldName]
                    ?: return null

            return try {

                property.isAccessible =
                    true

                property
                    .getter
                    .call(entity)
                    ?.toString()
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            } catch (_: Exception) {

                null
            }
        }

        val annotatedDisplayField =
            entity::class
                .memberProperties
                .firstOrNull {
                        property ->

                    property.isAccessible =
                        true

                    property.javaField
                        ?.getAnnotation(
                            KraftAdminField::class.java
                        )
                        ?.displayField == true
                }
                ?.name

        val label =
            value(
                annotatedDisplayField
            )
                ?: value(
                    preferredDisplayField
                )
                ?: labelCandidates
                    .firstNotNullOfOrNull(
                        ::value
                    )
                ?: id

        return ObjectResponse(
            id = id,
            label = label
        )
    }

    private fun extractId(
        entity: Any
    ): Any? {

        val property =
            entity::class
                .memberProperties
                .firstOrNull {

                    it.findAnnotation<Id>() != null ||

                            it.name.equals(
                                "id",
                                ignoreCase = true
                            ) ||

                            it.name == "_id"
                }
                ?: return null

        return try {

            property.isAccessible =
                true

            property
                .getter
                .call(entity)

        } catch (e: Exception) {

            logger.debug(
                "Could not extract Mongo ID from {}: {}",
                entity::class.simpleName,
                e.message
            )

            null
        }
    }
}