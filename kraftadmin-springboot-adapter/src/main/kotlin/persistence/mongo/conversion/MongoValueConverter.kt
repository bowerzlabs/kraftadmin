package persistence.mongo.conversion

import api.utils.ObjectResponse
import api.utils.ResourceRow
import com.kraftadmin.logging.KraftAdminLogging
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.DocumentReference
import java.lang.reflect.Modifier
import java.time.temporal.Temporal
import java.util.Date
import java.util.IdentityHashMap
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

/**
 * Converts Mongo entities into frontend-safe KraftAdmin values.
 *
 * Conversion behavior depends on the target context:
 *
 * TABLE
 * - Compact scalar values
 * - Compact RelatedItem values for relations
 *
 * DETAIL
 * - Complete scalar values
 * - Expanded RelatedItem values for relations
 *
 * FORM
 * - ObjectResponse values for relations
 */
object MongoValueConverter {

    private val logger =
        KraftAdminLogging.logger(javaClass)

    /**
     * Prevents infinite recursion when Mongo entities contain
     * circular references.
     */
    private val activeObjects =
        ThreadLocal.withInitial {
            IdentityHashMap<Any, Boolean>()
        }

    /**
     * Converts a property according to its Mongo metadata
     * and the target response context.
     */
    fun convert(
        property: KProperty1<Any, *>,
        rawValue: Any?,
        mode: MongoConversionMode
    ): Any? {

        if (rawValue == null) {
            return null
        }

        val isRelation =
            property.findAnnotation<DBRef>() != null ||
                    property.findAnnotation<DocumentReference>() != null

        return when {

            isRelation &&
                    rawValue is Collection<*> ->
                convertRelationCollection(
                    values = rawValue,
                    mode = mode
                )

            isRelation &&
                    rawValue is Array<*> ->
                rawValue.map {
                        value ->
                    value?.let {
                        convertRelation(
                            value = it,
                            mode = mode
                        )
                    }
                }

            isRelation ->
                convertRelation(
                    value = rawValue,
                    mode = mode
                )

            rawValue is Collection<*> ->
                convertCollection(
                    values = rawValue,
                    mode = mode
                )

            rawValue is Array<*> ->
                rawValue.map {
                        value ->
                    convertValue(
                        value = value,
                        mode = mode
                    )
                }

            else ->
                convertValue(
                    value = rawValue,
                    mode = mode
                )
        }
    }

    /**
     * Recursively converts a non-relation value.
     */
    fun convertValue(
        value: Any?,
        mode: MongoConversionMode =
            MongoConversionMode.DETAIL
    ): Any? {

        if (value == null) {
            return null
        }

        return when (value) {

            is ObjectId ->
                value.toHexString()

            is Enum<*> ->
                value.name

            is String,
            is Number,
            is Boolean,
            is Char ->
                value

            is Date ->
                value

            is Temporal ->
                value.toString()

            is Collection<*> ->
                convertCollection(
                    values = value,
                    mode = mode
                )

            is Array<*> ->
                value.map {
                        item ->
                    convertValue(
                        value = item,
                        mode = mode
                    )
                }

            is Map<*, *> ->
                convertMap(
                    values = value,
                    mode = mode
                )

            else ->
                mapObjectToValues(
                    entity = value,
                    mode = mode
                )
        }
    }

    /**
     * Converts a Mongo relation according to the response context.
     */
    private fun convertRelation(
        value: Any,
        mode: MongoConversionMode
    ): Any? {

        return when (mode) {

            MongoConversionMode.FORM ->
                createObjectResponse(value)

            MongoConversionMode.TABLE ->
                createCompactRelatedItem(value)

            MongoConversionMode.DETAIL ->
                createDetailedRelatedItem(value)
        }
    }

    /**
     * Converts a collection that belongs to a Mongo relation.
     */
    private fun convertRelationCollection(
        values: Collection<*>,
        mode: MongoConversionMode
    ): List<Any?> {

        return values.map {
                value ->

            value?.let {
                convertRelation(
                    value = it,
                    mode = mode
                )
            }
        }
    }

    /**
     * Converts an ordinary collection.
     *
     * This is used for collections that are not annotated
     * with @DBRef or @DocumentReference.
     */
    private fun convertCollection(
        values: Collection<*>,
        mode: MongoConversionMode
    ): List<Any?> {

        return values.map {
                value ->

            convertValue(
                value = value,
                mode = mode
            )
        }
    }

    /**
     * Creates the lightweight representation used by forms.
     */
    private fun createObjectResponse(
        entity: Any
    ): ObjectResponse? {

        val id =
            extractIdentifier(entity)
                ?.toString()
                ?: return null

        return ObjectResponse(
            id = id,
            label =
                resolveDisplayLabel(entity)
                    ?: id
        )
    }

    /**
     * Creates a compact relation representation for tables.
     *
     * The entity type and ID are still included so that table
     * relations can be navigated or managed.
     */
    private fun createCompactRelatedItem(
        entity: Any
    ): ResourceRow.RelatedItem? {

        val id =
            extractIdentifier(entity)
                ?.toString()
                ?: return null

        val displayLabel =
            resolveDisplayLabel(entity)
                ?: id

        return ResourceRow.RelatedItem(
            id = id,
            entityType =
                resolveEntityType(entity),
            displayLabel = displayLabel,
            values =
                mapOf(
                    "id" to id,
                    "label" to displayLabel
                )
        )
    }

    /**
     * Creates the complete relation representation for
     * resource detail pages.
     */
    private fun createDetailedRelatedItem(
        entity: Any
    ): ResourceRow.RelatedItem? {

        val id =
            extractIdentifier(entity)
                ?.toString()
                ?: return null

        return ResourceRow.RelatedItem(
            id = id,
            entityType =
                resolveEntityType(entity),
            displayLabel =
                resolveDisplayLabel(entity)
                    ?: id,
            values =
                mapObjectToValues(
                    entity = entity,
                    mode =
                        MongoConversionMode.DETAIL
                )
        )
    }

    /**
     * Resolves the resource entity name.
     *
     * For example:
     *
     * JournalEntry
     * User
     * Product
     */
    private fun resolveEntityType(
        entity: Any
    ): String {

        return entity::class
            .simpleName
            ?: entity::class
                .qualifiedName
            ?: "Unknown"
    }

    /**
     * Converts maps into frontend-safe maps.
     */
    private fun convertMap(
        values: Map<*, *>,
        mode: MongoConversionMode
    ): Map<String, Any?> {

        return values.entries.associate {
                (key, value) ->

            key.toString() to
                    convertValue(
                        value = value,
                        mode = mode
                    )
        }
    }

    /**
     * Converts all fields of an entity.
     */
    fun mapObjectToValues(
        entity: Any?,
        mode: MongoConversionMode =
            MongoConversionMode.DETAIL
    ): Map<String, Any?> {

        if (entity == null) {
            return emptyMap()
        }

        val visited =
            activeObjects.get()

        if (visited.containsKey(entity)) {

            logger.debug(
                "Circular Mongo object reference detected: {}",
                entity::class.qualifiedName
            )

            return emptyMap()
        }

        visited[entity] = true

        return try {

            entity::class
                .memberProperties
                .filterNot {
                    Modifier.isStatic(
                        it.javaField
                            ?.modifiers
                            ?: 0
                    )
                }
                .associate {
                        property ->

                    property.isAccessible =
                        true

                    property.name to
                            try {

                                @Suppress(
                                    "UNCHECKED_CAST"
                                )
                                val typedProperty =
                                    property as
                                            KProperty1<Any, *>

                                convert(
                                    property =
                                        typedProperty,
                                    rawValue =
                                        property
                                            .getter
                                            .call(entity),
                                    mode =
                                        mode
                                )

                            } catch (e: Exception) {

                                logger.debug(
                                    "Skipping Mongo field {}: {}",
                                    property.name,
                                    e.message
                                )

                                null
                            }
                }

        } finally {

            visited.remove(entity)

            if (visited.isEmpty()) {
                activeObjects.remove()
            }
        }
    }

    /**
     * Converts only the requested fields.
     */
    fun mapObjectToValues(
        entity: Any?,
        fieldsToRead: Collection<String>,
        mode: MongoConversionMode =
            MongoConversionMode.TABLE
    ): Map<String, Any?> {

        if (entity == null) {
            return emptyMap()
        }

        val wantedFields =
            fieldsToRead.toHashSet()

        return entity::class
            .memberProperties
            .filter {
                    property ->
                property.name in
                        wantedFields
            }
            .associate {
                    property ->

                property.isAccessible =
                    true

                property.name to
                        try {

                            @Suppress(
                                "UNCHECKED_CAST"
                            )
                            val typedProperty =
                                property as
                                        KProperty1<Any, *>

                            convert(
                                property =
                                    typedProperty,
                                rawValue =
                                    property
                                        .getter
                                        .call(entity),
                                mode =
                                    mode
                            )

                        } catch (e: Exception) {

                            logger.debug(
                                "Skipping Mongo field {}: {}",
                                property.name,
                                e.message
                            )

                            null
                        }
            }
    }

    /**
     * Extracts an identifier from:
     *
     * - Mongo DBRef
     * - Mongo reference maps
     * - @Id properties
     * - id properties
     * - _id properties
     */
    fun extractIdentifier(
        entity: Any
    ): Any? {

        if (entity is com.mongodb.DBRef) {
            return entity.id
        }

        if (entity is Map<*, *>) {

            return entity["id"]
                ?: entity["_id"]
                ?: entity["\$id"]
        }

        val property =
            entity::class
                .memberProperties
                .firstOrNull {

                    it.findAnnotation<Id>() !=
                            null ||

                            it.name.equals(
                                "id",
                                ignoreCase =
                                    true
                            ) ||

                            it.name ==
                            "_id"
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
                "Could not extract Mongo identifier " +
                        "from {}: {}",
                entity::class.simpleName,
                e.message
            )

            null
        }
    }

    /**
     * Resolves a readable relation label.
     */
    fun resolveDisplayLabel(
        entity: Any
    ): String? {

        if (entity is com.mongodb.DBRef) {
            return entity.id
                ?.toString()
        }

        if (entity is Map<*, *>) {

            return (
                    entity["displayName"]
                        ?: entity["name"]
                        ?: entity["title"]
                        ?: entity["label"]
                        ?: entity["username"]
                        ?: entity["email"]
                        ?: entity["id"]
                        ?: entity["_id"]
                    )
                ?.toString()
                ?.trim()
        }

        val preferredFields =
            listOf(
                "displayName",
                "name",
                "title",
                "label",
                "username",
                "email"
            )

        for (fieldName in preferredFields) {

            val property =
                entity::class
                    .memberProperties
                    .firstOrNull {

                        it.name.equals(
                            fieldName,
                            ignoreCase =
                                true
                        )
                    }
                    ?: continue

            try {

                property.isAccessible =
                    true

                val value =
                    property
                        .getter
                        .call(entity)
                        ?.toString()
                        ?.trim()

                if (!value.isNullOrBlank()) {
                    return value
                }

            } catch (_: Exception) {
                // Try the next label field.
            }
        }

        return null
    }
}