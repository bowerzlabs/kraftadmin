package persistence.mongo.conversion

import com.kraftadmin.logging.KraftAdminLogging
import com.mongodb.DBRef
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.core.mapping.DBRef as MongoDbRefAnnotation
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID

/**
 * Converts normalized KraftAdmin form values into values compatible with
 * MongoDB entity mappings.
 *
 * The input should already have passed through FormDataCoercer:
 *
 * ObjectResponse -> ID
 * { id, label } -> ID
 * EmbeddedResponse -> plain map
 */
object MongoTypeConverter {

    private val logger =
        KraftAdminLogging.logger(javaClass)

    /**
     * Converts all supplied fields according to the entity's Java fields.
     *
     * Fields inherited from a superclass are included.
     */
    fun coerceForEntity(
        entityClass: Class<*>,
        data: Map<String, Any?>
    ): Map<String, Any?> {

        val fieldsByName =
            fieldsOf(entityClass)
                .associateBy { it.name }

        return data.mapValues { (fieldName, value) ->

            val field =
                fieldsByName[fieldName]
                    ?: return@mapValues value

            try {

                convert(
                    value = value,
                    field = field
                )

            } catch (e: Exception) {

                logger.warn(
                    "Could not convert Mongo field '{}': {}",
                    fieldName,
                    e.message
                )

                value
            }
        }
    }

    /**
     * Converts one form value according to its Mongo entity field.
     */
    fun convert(
        value: Any?,
        field: Field
    ): Any? {

        if (value == null) {
            return null
        }

        val isDbRef =
            field.isAnnotationPresent(
                MongoDbRefAnnotation::class.java
            )

        val isDocumentReference =
            field.isAnnotationPresent(
                DocumentReference::class.java
            )

        val isRelation =
            isDbRef || isDocumentReference

        val isCollection =
            Collection::class.java
                .isAssignableFrom(field.type)

        return when {

            isRelation && isCollection ->

                convertRelationCollection(
                    value = value,
                    field = field,
                    useDbRef = isDbRef
                )

            isRelation ->

                convertRelationSingle(
                    value = value,
                    targetClass = field.type,
                    useDbRef = isDbRef
                )

            isCollection ->

                convertCollection(
                    value = value,
                    field = field
                )

            else ->

                convertScalar(
                    value = value,
                    targetType = field.type
                )
        }
    }

    // Mongo relations

    private fun convertRelationSingle(
        value: Any?,
        targetClass: Class<*>,
        useDbRef: Boolean
    ): Any? {

        val rawId =
            extractReferenceId(value)
                ?: return null

        val typedId =
            convertReferenceId(
                rawId = rawId,
                targetClass = targetClass
            )
                ?: return null

        return if (useDbRef) {

            DBRef(
                targetCollectionName(targetClass),
                typedId
            )

        } else {

            /*
             * @DocumentReference stores the referenced ID directly.
             *
             * Example:
             *
             * journalEntries:
             * [
             *     ObjectId("6a6b2f57ad053da174ee8609")
             * ]
             */
            typedId
        }
    }

    private fun convertRelationCollection(
        value: Any?,
        field: Field,
        useDbRef: Boolean
    ): List<Any> {
        val targetClass =
            resolveCollectionElementClass(field)
                ?: throw IllegalArgumentException(
                    "Could not determine relation element type " +
                            "for ${field.declaringClass.simpleName}.${field.name}"
                )
        val values =
            normalizeToList(value)
        return values.mapNotNull { item ->
            val rawId =
                extractReferenceId(item)
                    ?: return@mapNotNull null
            val typedId =
                convertReferenceId(
                    rawId = rawId,
                    targetClass = targetClass
                )
                    ?: return@mapNotNull null

            // FIX: Wrap inside DBRef if useDbRef is true, matching single relation behavior
            if (useDbRef) {
                DBRef(
                    targetCollectionName(targetClass),
                    typedId
                )
            } else {
                typedId
            }
        }
    }
    /**
     * Converts a relation ID using the actual @Id type of the target
     * Mongo entity.
     *
     * This supports:
     *
     * ObjectId
     * String
     * UUID
     * Long
     * Integer
     */
    private fun convertReferenceId(
        rawId: Any?,
        targetClass: Class<*>
    ): Any? {

        if (rawId == null) {
            return null
        }

        val idType =
            resolveIdType(targetClass)

        if (
            idType != null &&
            idType.isInstance(rawId)
        ) {
            return rawId
        }

        val value =
            rawId.toString()
                .trim()

        if (value.isBlank()) {
            return null
        }

        return try {

            when (idType) {

                ObjectId::class.java ->
                    ObjectId(value)

                String::class.java ->
                    value

                UUID::class.java ->
                    UUID.fromString(value)

                Long::class.java,
                java.lang.Long::class.java ->
                    value.toLong()

                Int::class.java,
                java.lang.Integer::class.java ->
                    value.toInt()

                else -> {

                    /*
                     * If the target entity does not explicitly expose an
                     * ID type, use ObjectId when the value is a valid BSON
                     * ObjectId. Otherwise preserve the original string.
                     */
                    if (ObjectId.isValid(value)) {
                        ObjectId(value)
                    } else {
                        value
                    }
                }
            }

        } catch (e: Exception) {

            logger.warn(
                "Could not convert relation ID '{}' for {}: {}",
                value,
                targetClass.simpleName,
                e.message
            )

            null
        }
    }

    /**
     * Finds the @Id field in the target entity or its superclasses.
     */
    private fun resolveIdType(
        targetClass: Class<*>
    ): Class<*>? {

        var currentClass: Class<*>? =
            targetClass

        while (
            currentClass != null &&
            currentClass != Any::class.java
        ) {

            val idField =
                currentClass.declaredFields
                    .firstOrNull { field ->

                        field.isAnnotationPresent(
                            Id::class.java
                        ) ||

                                field.name == "id" ||

                                field.name == "_id"
                    }

            if (idField != null) {
                return idField.type
            }

            currentClass =
                currentClass.superclass
        }

        return null
    }

    private fun targetCollectionName(
        targetClass: Class<*>
    ): String {

        val document =
            targetClass.getAnnotation(
                Document::class.java
            )

        return document
            ?.collection
            ?.takeIf {
                it.isNotBlank()
            }
            ?: targetClass.simpleName
    }

    private fun resolveCollectionElementClass(
        field: Field
    ): Class<*>? {

        return resolveRawClass(
            field.genericType
        )
    }

    private fun resolveRawClass(
        type: Type
    ): Class<*>? {

        return when (type) {

            is ParameterizedType -> {

                val elementType =
                    type.actualTypeArguments
                        .firstOrNull()
                        ?: return null

                when (elementType) {

                    is Class<*> ->
                        elementType

                    is ParameterizedType ->
                        elementType.rawType
                                as? Class<*>

                    else ->
                        null
                }
            }

            else ->
                null
        }
    }

    private fun extractReferenceId(
        value: Any?
    ): Any? {

        return when (value) {

            null ->
                null

            is Map<*, *> ->
                value["id"]
                    ?: value["_id"]

            else ->
                value
        }
    }

    private fun normalizeToList(
        value: Any?
    ): List<Any?> {

        return when (value) {

            null ->
                emptyList()

            is Collection<*> ->
                value.toList()

            is Array<*> ->
                value.toList()

            else ->
                listOf(value)
        }
    }

    // Normal collections

    private fun convertCollection(
        value: Any?,
        field: Field
    ): List<Any?> {

        val elementType =
            resolveCollectionElementClass(field)

        val values =
            normalizeToList(value)

        if (elementType == null) {
            return values
        }

        return values.map { item ->

            when {

                item == null ->
                    null

                isScalarTarget(elementType) ->

                    convertScalar(
                        value = item,
                        targetType = elementType
                    )

                item is Map<*, *> -> {

                    @Suppress("UNCHECKED_CAST")
                    coerceForEntity(
                        entityClass = elementType,
                        data = item as Map<String, Any?>
                    )
                }

                else ->
                    item
            }
        }
    }

    // Embedded objects

    private fun convertEmbedded(
        value: Map<*, *>,
        targetType: Class<*>
    ): Map<String, Any?> {

        @Suppress("UNCHECKED_CAST")
        return coerceForEntity(
            entityClass = targetType,
            data = value as Map<String, Any?>
        )
    }

    // Scalar values

    fun convertScalar(
        value: Any?,
        targetType: Class<*>
    ): Any? {

        if (value == null) {
            return null
        }

        if (targetType.isInstance(value)) {
            return value
        }

        if (
            !isScalarTarget(targetType) &&
            value is Map<*, *>
        ) {

            return convertEmbedded(
                value = value,
                targetType = targetType
            )
        }

        val stringValue =
            value.toString()
                .trim()

        if (stringValue.isEmpty()) {
            return null
        }

        return try {

            when {

                targetType == String::class.java ->
                    stringValue

                targetType == ObjectId::class.java ->
                    ObjectId(stringValue)

                targetType == UUID::class.java ->
                    UUID.fromString(stringValue)

                targetType == Boolean::class.java ||
                        targetType == java.lang.Boolean::class.java ->

                    stringValue.equals(
                        "true",
                        ignoreCase = true
                    ) ||
                            stringValue == "1"

                targetType == Int::class.java ||
                        targetType == java.lang.Integer::class.java ->

                    stringValue.toInt()

                targetType == Long::class.java ||
                        targetType == java.lang.Long::class.java ->

                    stringValue.toLong()

                targetType == Double::class.java ||
                        targetType == java.lang.Double::class.java ->

                    stringValue.toDouble()

                targetType == Float::class.java ||
                        targetType == java.lang.Float::class.java ->

                    stringValue.toFloat()

                targetType == Short::class.java ||
                        targetType == java.lang.Short::class.java ->

                    stringValue.toShort()

                targetType == LocalDate::class.java ->
                    LocalDate.parse(stringValue)

                targetType == LocalDateTime::class.java ->

                    try {

                        LocalDateTime.parse(
                            stringValue
                        )

                    } catch (_: Exception) {

                        LocalDate
                            .parse(stringValue)
                            .atStartOfDay()
                    }

                targetType == Instant::class.java ->
                    Instant.parse(stringValue)

                targetType == Date::class.java ->
                    Date.from(
                        Instant.parse(stringValue)
                    )

                targetType.isEnum -> {

                    @Suppress("UNCHECKED_CAST")
                    (
                            targetType
                                    as Class<Enum<*>>
                            )
                        .enumConstants
                        ?.firstOrNull {

                            it.name.equals(
                                stringValue,
                                ignoreCase = true
                            )
                        }
                }

                else ->
                    value
            }

        } catch (e: Exception) {

            logger.warn(
                "Failed converting '{}' to {}: {}",
                value,
                targetType.simpleName,
                e.message
            )

            value
        }
    }

    // Reflection helpers

    private fun fieldsOf(
        entityClass: Class<*>
    ): List<Field> {

        val fields =
            mutableListOf<Field>()

        var currentClass: Class<*>? =
            entityClass

        while (
            currentClass != null &&
            currentClass != Any::class.java
        ) {

            currentClass
                .declaredFields
                .filterNot {

                    Modifier.isStatic(
                        it.modifiers
                    ) ||
                            it.isSynthetic
                }
                .forEach {

                    it.isAccessible = true

                    fields.add(it)
                }

            currentClass =
                currentClass.superclass
        }

        return fields
    }

    private fun isScalarTarget(
        type: Class<*>
    ): Boolean {

        return type ==
                String::class.java ||

                type ==
                ObjectId::class.java ||

                type ==
                UUID::class.java ||

                Number::class.java
                    .isAssignableFrom(type) ||

                type.isPrimitive ||

                type ==
                Boolean::class.java ||

                type.isEnum ||

                type ==
                LocalDate::class.java ||

                type ==
                LocalDateTime::class.java ||

                type ==
                Instant::class.java ||

                Date::class.java
                    .isAssignableFrom(type)
    }
}