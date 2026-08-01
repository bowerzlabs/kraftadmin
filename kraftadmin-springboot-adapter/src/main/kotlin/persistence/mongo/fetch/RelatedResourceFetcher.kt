package persistence.mongo.fetch

import api.utils.ResourceRow
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.ui_descriptors.LookupDescriptor
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import persistence.mongo.conversion.MongoValueConverter
import persistence.mongo.metadata.MongoEntityMetadata
import persistence.mongo.metadata.MongoPropertyResolver
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Fetches *collection* relations (@DBRef / @DocumentReference on a
 * List/Set) for a Mongo detail view. Mongo counterpart to
 * persistence.jpa.fetch.RelatedResourceFetcher.
 *
 * Deliberately does not touch singular relation fields (a single
 * Project or AdUser) — those are resolved inline as ObjectResponse by
 * MongoResourceRowMapper directly, same in table and detail mode.
 * This fetcher exists only for the "many" side, which is exactly the
 * side that's unsafe to resolve in a table row (Task.watchers is the
 * motivating example).
 *
 * Only ever called from detail-view code paths (FetchById), never
 * from FetchAll/MongoQueryBuilder.
 */
class MongoRelatedResourceFetcher(private val limit: Int = 10) {

    private val logger = KraftAdminLogging.logger(javaClass)

    /**
     * Extracts all collection-typed @DBRef / @DocumentReference
     * relations from [entity] and maps them to RelatedCollections.
     */
    fun fetch(entity: Any): Map<String, ResourceRow.RelatedCollection> {
        val result = mutableMapOf<String, ResourceRow.RelatedCollection>()

        entity::class.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach
            if (MongoPropertyResolver.shouldSkip(field)) return@forEach

            val isRelation =
                field.isAnnotationPresent(DBRef::class.java) ||
                        field.isAnnotationPresent(DocumentReference::class.java)

            if (!isRelation) return@forEach

            // Singular relations are out of scope for this fetcher —
            // they're resolved inline by MongoResourceRowMapper instead.
            if (!Collection::class.java.isAssignableFrom(field.type)) return@forEach

            val relatedKClass = resolveCollectionElementClass(field) ?: return@forEach

            val isDocumentEntity = relatedKClass.java.isAnnotationPresent(Document::class.java)
            if (!isDocumentEntity) return@forEach

            val rawValue = try {
                field.isAccessible = true
                // Reading the field returns whatever Spring Data put
                // there — for lazy = true this is an unresolved proxy
                // until mapToRelatedItem() below calls a getter on it.
                field.get(entity)
            } catch (e: Exception) {
                logger.debug(
                    "Could not read relation field {} on {}: {}",
                    field.name,
                    entity::class.simpleName,
                    e.message
                )
                null
            }

            val itemsList = (rawValue as? Collection<*>)?.filterNotNull() ?: emptyList()
            val sliced = itemsList.take(limit)
            val relatedItems = sliced.mapNotNull { mapToRelatedItem(it, relatedKClass) }

            @Suppress("UNCHECKED_CAST")
            val relatedMetadata = try {
                MongoEntityMetadata(relatedKClass as KClass<Any>)
            } catch (e: Exception) {
                logger.debug(
                    "Could not build metadata for related type {}: {}",
                    relatedKClass.simpleName,
                    e.message
                )
                null
            }

            val lookupDescriptor = LookupDescriptor(
                targetEntity = relatedMetadata?.entityName ?: relatedKClass.simpleName,
                lookupKey = relatedMetadata?.idField ?: "id",
                displayField = relatedMetadata?.displayField ?: "id",
                searchableFields = relatedMetadata?.searchableFields ?: emptyList()
            )

            result[prop.name] = ResourceRow.RelatedCollection(
                fieldName = prop.name,
                entityType = relatedKClass.simpleName ?: "Unknown",
                items = relatedItems,
                totalInMemory = itemsList.size,
                limited = itemsList.size > limit,
                lookupDescriptor = lookupDescriptor
            )
        }

        return result
    }

    private fun resolveCollectionElementClass(field: Field): KClass<*>? {
        val parameterized = field.genericType as? ParameterizedType ?: return null
        val argument = parameterized.actualTypeArguments.firstOrNull() ?: return null

        return when (argument) {
            is Class<*> -> argument.kotlin
            is ParameterizedType -> (argument.rawType as? Class<*>)?.kotlin
            is WildcardType -> (argument.upperBounds.firstOrNull() as? Class<*>)?.kotlin
            else -> null
        }
    }

    private fun mapToRelatedItem(item: Any, relatedKClass: KClass<*>): ResourceRow.RelatedItem? {
        // Calling extractIdentifier/resolveDisplayLabel is what actually
        // forces a lazy DBRef/DocumentReference proxy to resolve, since
        // they call getters on `item`.
        val id = MongoValueConverter.extractIdentifier(item)?.toString() ?: return null
        val label = MongoValueConverter.resolveDisplayLabel(item) ?: id

        val values = mutableMapOf<String, Any?>()

        item::class.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach
            if (MongoPropertyResolver.shouldSkip(field)) return@forEach

            if (field.isAnnotationPresent(DBRef::class.java) ||
                field.isAnnotationPresent(DocumentReference::class.java)
            ) return@forEach

            val classifier = prop.returnType.classifier as? KClass<*> ?: return@forEach
            if (!MongoPropertyResolver.isSimpleType(classifier)) return@forEach

            try {
                field.isAccessible = true
                values[prop.name] = field.get(item)
            } catch (e: Exception) {
                logger.debug(
                    "Could not read field {} on {}",
                    field.name,
                    item::class.simpleName
                )
            }
        }

        return ResourceRow.RelatedItem(
            id = id,
            entityType = relatedKClass.simpleName ?: "Unknown",
            displayLabel = label,
            values = values
        )
    }
}