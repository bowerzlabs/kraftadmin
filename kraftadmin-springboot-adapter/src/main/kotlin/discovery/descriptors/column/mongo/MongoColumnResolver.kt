package discovery.descriptors.column.mongo

import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.ui_descriptors.ColumnDescriptor
import com.kraftadmin.ui_descriptors.LookupDescriptor
import com.kraftadmin.ui_descriptors.WYSIWYGOptions
import discovery.descriptors.column.jpa.ValidationResolver
import discovery.descriptors.column.resolvers.EnumHelper
import discovery.descriptors.column.resolvers.FileResolver
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.temporal.Temporal
import java.util.Date
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaField

class MongoColumnResolver(
    val fileResolver: FileResolver,
    val validationResolver: ValidationResolver,
    val mongoLookupResolver: MongoLookupResolver
) {

    private val log = KraftAdminLogging.logger(javaClass)

    private val maxEmbedDepth = 5

    fun resolve(
        entityClass: KClass<*>,
        property: KProperty1<out Any, *>
    ): KraftAdminColumn? {

        try {
            val field = property.javaField ?: return null

            if (
                field.isAnnotationPresent(Transient::class.java) ||
                Modifier.isStatic(field.modifiers)
            ) {
                return null
            }

            val isMongoReference =
                field.isAnnotationPresent(DBRef::class.java) ||
                        field.isAnnotationPresent(DocumentReference::class.java)

            val targetDocumentClass =
                if (isMongoReference) {
                    resolveReferenceTargetClass(property)
                } else {
                    null
                }


            val adminField: KraftAdminField? =
                property.findAnnotation<KraftAdminField>()
                    ?: field.getAnnotation(KraftAdminField::class.java)

            val isReferenceCollection =
                isMongoReference &&
                        Collection::class.java.isAssignableFrom(field.type)

            val lookup =
                mongoLookupResolver.buildLookup(
                    property = property,
                    targetDocumentClass = targetDocumentClass
                )

            val isIdField = field.isAnnotationPresent(org.springframework.data.annotation.Id::class.java) ||
                    property.name.equals("id", ignoreCase = true)
            val classifier = property.returnType.classifier as? KClass<*>

            val type = when {
                lookup != null && isReferenceCollection ->
                    FormInputType.MULTI_RELATION

                lookup != null ->
                    FormInputType.RELATION

                adminField?.inputType != null &&
                        adminField.inputType != FormInputType.UNSET ->
                    adminField.inputType

                else ->
                    resolveType(property)
            }

            val label = adminField?.label
                ?.takeIf { it.isNotBlank() }
                ?: humanize(property.name)

            val placeholder = adminField?.placeholder
                ?.takeIf { it.isNotBlank() }
                ?: "Enter ${property.name}"

            val searchable = adminField?.searchable ?: true
            val sortable = adminField?.sortable ?: true
            val required = adminField?.required ?: false
            val sensitive = adminField?.sensitive ?: false

            val showInTable = if (adminField != null) {
                adminField.showInTable || !isIdField
            } else {
                !isIdField
            }

            val validation = validationResolver.resolve(field)

            val selectOptions =
                if (type == FormInputType.SELECT) {
                    val enumClass = classifier?.java
                    if (enumClass?.isEnum == true) {
                        @Suppress("UNCHECKED_CAST")
                        EnumHelper.getSelectOptions(enumClass as Class<out Enum<*>>)
                    } else null
                } else null

            val wysiwyg =
                if (type == FormInputType.WYSIWYG) {
                    adminField?.wysiwygConfig?.let {
                        WYSIWYGOptions(
                            toolbar = it.toolbarProfile.name,
                            placeholder = it.placeholder.ifBlank { "Enter ${property.name}" },
                            options = it.toolbarProfile.toolbarConfig
                        )
                    }
                } else null

            val fileOptions = fileResolver.resolve(type, adminField?.fileConfig)

            // --- Recursive subColumns for embedded objects and embedded object lists ---
            // Only relevant when this ISN'T a relation — a @DBRef/manual reference
            // field should never also try to expand into embedded subColumns.
            val subColumns: List<ColumnDescriptor>? = when {
                lookup != null -> null

                type == FormInputType.OBJECT && classifier != null && !isScalarLike(classifier) ->
                    resolveEmbeddedColumns(classifier, visited = setOf(entityClass), depth = 1)

                type == FormInputType.COLLECTION -> {
                    val elementClass = property.returnType.arguments
                        .firstOrNull()?.type?.classifier as? KClass<*>
                    if (elementClass != null && !isScalarLike(elementClass)) {
                        resolveEmbeddedColumns(elementClass, visited = setOf(entityClass), depth = 1)
                    } else null
                }

                else -> null
            }

            return KraftAdminColumn(
                name = property.name,
                label = label,
                type = type,
                searchable = searchable,
                sortable = sortable,
                visible = !sensitive && (!isIdField || adminField?.showInTable == true),
                showInTable = showInTable,
                required = required,
                defaultValue = null,
                selectOptions = selectOptions,
                subColumns = subColumns,
                placeholder = placeholder,
                validationRules = validation.rules,
                validationMessages = validation.messages,
                lookup = lookup,
                wysiwygConfigValue = wysiwyg,
                fileOptions = fileOptions,
                elementCollection = null
            )

        } catch (e: Exception) {
            log.warn(
                "Error resolving MongoDB column {} for {}: {}",
                property.name,
                entityClass.qualifiedName,
                e.message
            )
            return null
        }
    }

    private fun resolveEmbeddedColumns(
        embeddedClass: KClass<*>,
        visited: Set<KClass<*>>,
        depth: Int
    ): List<ColumnDescriptor>? {
        if (depth > maxEmbedDepth || embeddedClass in visited) {
            log.warn("Skipping embedded resolution for {} — max depth or cycle detected", embeddedClass.qualifiedName)
            return null
        }

        val fields = generateSequence(embeddedClass.java as Class<*>) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.toList() }
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic || it.isAnnotationPresent(Transient::class.java) }
            .onEach { it.isAccessible = true }
            .toList()

        val nextVisited = visited + embeddedClass

        return fields.mapNotNull { field ->
            resolveEmbeddedFieldColumn(field, nextVisited, depth)
        }
    }

    private fun resolveEmbeddedFieldColumn(
        field: Field,
        visited: Set<KClass<*>>,
        depth: Int
    ): ColumnDescriptor? {
        try {
            val adminField = field.getAnnotation(KraftAdminField::class.java)
            val fieldClass = field.type.kotlin

            val type = adminField?.inputType
                ?.takeIf { it != FormInputType.UNSET }
                ?: resolveJavaType(field)

            val label = adminField?.label?.takeIf { it.isNotBlank() } ?: humanize(field.name)
            val placeholder = adminField?.placeholder?.takeIf { it.isNotBlank() } ?: "Enter ${field.name}"

            val selectOptions =
                if (type == FormInputType.SELECT && field.type.isEnum) {
                    @Suppress("UNCHECKED_CAST")
                    EnumHelper.getSelectOptions(field.type as Class<out Enum<*>>)
                } else null

            val subColumns = when {
                type == FormInputType.OBJECT && !isScalarLikeJava(field.type) ->
                    resolveEmbeddedColumns(fieldClass, visited, depth + 1)

                type == FormInputType.COLLECTION -> {
                    val elementClass = (field.genericType as? ParameterizedType)
                        ?.actualTypeArguments?.firstOrNull() as? Class<*>
                    if (elementClass != null && !isScalarLikeJava(elementClass)) {
                        resolveEmbeddedColumns(elementClass.kotlin, visited, depth + 1)
                    } else null
                }

                else -> null
            }

            return ColumnDescriptor(
                name = field.name,
                label = label,
                type = type.name,
                searchable = adminField?.searchable ?: true,
                sortable = adminField?.sortable ?: true,
                visible = adminField?.sensitive != true,
                showInTable = false,
                required = adminField?.required ?: false,
                defaultValue = null,
                selectOptions = selectOptions,
                subColumns = subColumns,
                placeholder = placeholder,
                validationRules = adminField?.regex ?: "",
                validationMessages = emptyMap(),
                lookup = null,
                wysiwygConfig = null,
                fileOptions = null,
            )
        } catch (e: Exception) {
            log.warn("Error resolving embedded field {}: {}", field.name, e.message)
            return null
        }
    }

    private fun isScalarLike(kClass: KClass<*>): Boolean = isScalarLikeJava(kClass.java)

    private fun isScalarLikeJava(javaClass: Class<*>): Boolean {
        return javaClass == String::class.java ||
                javaClass == org.bson.types.ObjectId::class.java ||
                javaClass == java.util.UUID::class.java ||
                Number::class.java.isAssignableFrom(javaClass) ||
                javaClass.isPrimitive ||
                javaClass == Boolean::class.java ||
                javaClass.isEnum ||
                Temporal::class.java.isAssignableFrom(javaClass) ||
                Date::class.java.isAssignableFrom(javaClass) ||
                Map::class.java.isAssignableFrom(javaClass)
    }

    private fun humanize(propertyName: String): String =
        propertyName
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }

    private fun resolveType(property: KProperty1<out Any, *>): FormInputType {
        val classifier = property.returnType.classifier as? KClass<*>
            ?: return FormInputType.TEXT
        return classifyByClass(classifier.java)
    }

    private fun resolveJavaType(field: Field): FormInputType = classifyByClass(field.type)

    private fun classifyByClass(javaClass: Class<*>): FormInputType {
        return when {
            javaClass == String::class.java -> FormInputType.TEXT

            javaClass == org.bson.types.ObjectId::class.java -> FormInputType.TEXT
            javaClass == java.util.UUID::class.java -> FormInputType.TEXT

            javaClass == Int::class.java || javaClass == Integer::class.java ||
                    javaClass == Long::class.java || javaClass == java.lang.Long::class.java ||
                    javaClass == Double::class.java || javaClass == java.lang.Double::class.java ||
                    javaClass == Float::class.java || javaClass == java.lang.Float::class.java ->
                FormInputType.NUMBER

            javaClass == Boolean::class.java || javaClass == java.lang.Boolean::class.java ->
                FormInputType.CHECKBOX

            javaClass.isEnum -> FormInputType.SELECT

            javaClass == LocalDate::class.java ||
                    javaClass == LocalDateTime::class.java ||
                    javaClass == Instant::class.java -> FormInputType.DATE

            Collection::class.java.isAssignableFrom(javaClass) -> FormInputType.COLLECTION

            Map::class.java.isAssignableFrom(javaClass) -> FormInputType.JSON

            else -> FormInputType.OBJECT
        }
    }


    private fun resolveReferenceTargetClass(
        property: KProperty1<out Any, *>
    ): KClass<*>? {

        val classifier =
            property.returnType.classifier as? KClass<*> ?: return null

        if (!Collection::class.java.isAssignableFrom(classifier.java)) {
            return classifier
        }

        return property.returnType
            .arguments
            .firstOrNull()
            ?.type
            ?.classifier as? KClass<*>
    }

}