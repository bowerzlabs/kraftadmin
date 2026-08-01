package persistence.jpa.conversion

import api.utils.ObjectResponse
import com.kraftadmin.logging.KraftAdminLogging
import jakarta.persistence.*
import persistence.jpa.metadata.AssociationResolver
import persistence.jpa.util.HibernateUtil
import java.lang.reflect.Field

/**
 * Converts raw JPA field values into admin-UI-friendly representations.
 * Uses the canonical ObjectResponse from api.utils — no duplicate model.
 *
 * Entity → UI direction (read path).
 * Symmetric inverse is FormDataCoercer (write path).
 */
object ValueConverter {

    private val logger = KraftAdminLogging.logger(javaClass)


    /**
     * Wrapper for embedded value objects.
     * summary: human-readable preview for table cells
     * data:    full field map for the edit form
     */
    data class EmbeddedResponse(val summary: String, val data: Map<String, Any?>)

    fun convert(field: Field, rawValue: Any?): Any? {
        val value = HibernateUtil.unproxy(rawValue)

        return when {
            field.isAnnotationPresent(Embedded::class.java) ->
                convertEmbedded(value)

            field.isAnnotationPresent(ManyToOne::class.java) ||
                    field.isAnnotationPresent(OneToOne::class.java) ->
                convertSingleRelation(field, value)

            field.isAnnotationPresent(ElementCollection::class.java) ->
                convertElementCollection(value)

            field.isAnnotationPresent(ManyToMany::class.java) ||
                    field.isAnnotationPresent(OneToMany::class.java) ->
                convertCollectionRelation(value)

            else -> value
        }
    }

    private fun convertEmbedded(value: Any?): EmbeddedResponse? {
        if (value == null) return null
        val fullMap = mapEntityToValues(value)
        val summary = fullMap.values
            .filterIsInstance<String>()
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(", ")
        return EmbeddedResponse(summary, fullMap)
    }

    private fun convertSingleRelation(field: Field, value: Any?): ObjectResponse? {
        if (value == null) return null
        return try {
            val id = AssociationResolver.extractId(value)?.toString() ?: return null
            val label = AssociationResolver.resolveDisplayLabel(value) ?: id
            ObjectResponse(id = id, label = label)
        } catch (e: Exception) {
            logger.warn("Could not convert relation ${field.name}: ${e.message}")
            null
        }
    }

    private fun convertCollectionRelation(value: Any?): List<ObjectResponse?> {
        if (value !is Collection<*>) return emptyList()
        return value.map { item ->
            val real = HibernateUtil.unproxy(item) ?: return@map null
            val id = AssociationResolver.extractId(real)?.toString() ?: return@map null
            val label = AssociationResolver.resolveDisplayLabel(real) ?: id
            ObjectResponse(id = id, label = label)
        }
    }

    private fun convertElementCollection(value: Any?): Any? {
        if (value == null) return emptyList<Any>()

        return when (value) {
            is Map<*, *> -> convertElementCollectionMap(value)
            is Collection<*> -> convertElementCollectionList(value)
            else -> emptyList<Any>()
        }
    }

    private fun convertElementCollectionList(value: Collection<*>): List<Any?> {
        return value.map { element ->
            val real = HibernateUtil.unproxy(element)
            when {
                real == null -> null
                real.javaClass.isAnnotationPresent(Embeddable::class.java) -> mapEntityToValues(real)
                else -> real
            }
        }
    }

    // Emits the SAME {key, value} row shape the FE sends on write (FormDataCoercer / RelationshipWriter),
// so read and write are symmetric — no shape mismatch, no separate FE parsing branch needed.
    private fun convertElementCollectionMap(value: Map<*, *>): List<Map<String, Any?>> {
        return value.entries.map { (k, v) ->
            val realKey = HibernateUtil.unproxy(k)
            val realValue = HibernateUtil.unproxy(v)

            val convertedKey: Any? = when {
                realKey == null -> null
                realKey.javaClass.isAnnotationPresent(Embeddable::class.java) -> mapEntityToValues(realKey)
                else -> realKey
            }

            val convertedValue: Any? = when {
                realValue == null -> null
                realValue.javaClass.isAnnotationPresent(Embeddable::class.java) -> mapEntityToValues(realValue)
                else -> realValue
            }

            mapOf("key" to convertedKey, "value" to convertedValue)
        }
    }

    private fun convertElement(value: Any?): Any? {

        val real = HibernateUtil.unproxy(value)

        return when {

            real == null ->
                null

            real.javaClass.isAnnotationPresent(Embeddable::class.java) ->
                mapEntityToValues(real)

            else ->
                real
        }
    }

    private fun Map<*, *>.mapEntries(): Map<Any?, Any?> =
        entries.associate { (k, v) ->
            convertElement(k) to convertElement(v)
        }


    /**
     * Full entity → flat values map.
     * traverses the class hierarchy to include inherited fields.
     */
    fun mapEntityToValues(entity: Any?): Map<String, Any?> {
        if (entity == null) return emptyMap()

        val result = mutableMapOf<String, Any?>()
        var clazz: Class<*>? = entity.javaClass

        // Traverse the class hierarchy upwards
        while (clazz != null && clazz != Any::class.java) {
            // Assume PropertyResolver has a method that takes a Class type or
            // you use reflection directly here to get all declared fields
            for (field in clazz.declaredFields) {
                // Skip already mapped fields (shadowing) or transient fields
                if (result.containsKey(field.name) || java.lang.reflect.Modifier.isStatic(field.modifiers)) continue

                try {
                    field.isAccessible = true
                    val rawValue = field.get(entity)
                    // Use your existing ValueConverter.convert logic
                    result[field.name] = convert(field, rawValue)
                } catch (e: Exception) {
                    logger.debug("Skipping field ${field.name}: ${e.message}")
                }
            }
            clazz = clazz.superclass
        }
        return result
    }


    fun mapEntityToValues(
        entity: Any?,
        fieldsToRead: Collection<String>
    ): Map<String, Any?> {

        if (entity == null) return emptyMap()

        val wanted = fieldsToRead.toHashSet()
        val result = mutableMapOf<String, Any?>()

        var clazz: Class<*>? = entity.javaClass

        while (clazz != null && clazz != Any::class.java) {

            for (field in clazz.declaredFields) {

                if (field.name !in wanted) continue

                if (result.containsKey(field.name) ||
                    java.lang.reflect.Modifier.isStatic(field.modifiers)
                ) continue

                try {
                    field.isAccessible = true
                    val rawValue = field.get(entity)
                    result[field.name] = convert(field, rawValue)
                } catch (e: Exception) {
                    logger.debug("Skipping field ${field.name}: ${e.message}")
                }
            }

            clazz = clazz.superclass
        }

        return result
    }

}