package persistence.mongo.metadata

import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.spi.KraftEntityMetadata
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.DBRef
import org.springframework.data.mongodb.core.mapping.Document
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.UUID
import kotlin.reflect.KClass

class MongoEntityMetadata<T : Any>(
    private val entityClass: KClass<T>
) : KraftEntityMetadata<T> {

    private val allFields: List<Field> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val startClass: Class<*> = entityClass.java

        generateSequence(startClass) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.toList() }
            .onEach { it.isAccessible = true }
            .toList()
    }

    private val idFieldInternal: Field by lazy {
        allFields.find { it.isAnnotationPresent(Id::class.java) }
            ?: error("Entity ${entityClass.simpleName} must have a field annotated with @Id (org.springframework.data.annotation.Id)")
    }

    override val entityName: String by lazy {
        entityClass.java.getAnnotation(Document::class.java)?.collection?.takeIf { it.isNotBlank() }
            ?: entityClass.simpleName ?: "UnknownResource"
    }

    override val idField: String = idFieldInternal.name
    override val idType: Class<*> = idFieldInternal.type

    override val displayField: String by lazy {
        val display = allFields.filter { it.getAnnotation(KraftAdminField::class.java)?.displayField == true }
        when {
            display.isEmpty() -> idField
            display.size == 1 -> display.first().name
            else -> error("Multiple display fields in ${entityClass.simpleName}: ${display.map { it.name }}")
        }
    }

    override val versioningEnabled: Boolean
        get() = allFields.any { it.isAnnotationPresent(Version::class.java) }

    override val sortableFields: List<String> by lazy {
        allFields.filter { isSortable(it) && (it.getAnnotation(KraftAdminField::class.java)?.sortable ?: true) }
            .map { it.name }.distinct()
    }

    override val searchableFields: List<String> by lazy {
        allFields.filter { isSearchable(it) }
            .map { it.name }.take(5)
    }

    override val defaultSort: String by lazy { resolveDefaultSort() }

    override fun convertId(idValue: Any?): Any? {
        if (idValue == null) return null
        if (idValue is Map<*, *>) return null   // never a real id — treat as absent, not as a value to parse
        val value = idValue.toString()
        return try {
            when (idType) {
                ObjectId::class.java -> ObjectId(value)
                UUID::class.java -> UUID.fromString(value)
                Long::class.java, java.lang.Long::class.java -> value.toLong()
                Int::class.java, java.lang.Integer::class.java -> value.toInt()
                Short::class.java, java.lang.Short::class.java -> value.toShort()
                Byte::class.java, java.lang.Byte::class.java -> value.toByte()
                else -> value
            }
        } catch (e: Exception) {
            null  // couldn't parse — treat as "no valid id" rather than passing garbage through
        }
    }

    override fun getIdentifier(entity: T): Any? {
        return idFieldInternal.get(entity)
    }

    // Private Helpers

    private fun isRelation(f: Field) = f.isAnnotationPresent(DBRef::class.java)

    private fun isSkippable(f: Field): Boolean {
        val name = f.name
        return Modifier.isStatic(f.modifiers) ||
                Modifier.isTransient(f.modifiers) ||
                f.isSynthetic ||
                name == "\$stable" ||
                name.startsWith("this$")
    }

    private fun isSearchable(f: Field): Boolean {
        if (isSkippable(f) || isRelation(f)) return false
        val admin = f.getAnnotation(KraftAdminField::class.java)
        val isTypeValid = f.type == String::class.java || f.type.isEnum
        return if (admin != null) admin.searchable && isTypeValid else isTypeValid
    }

    private fun isSortable(f: Field): Boolean {
        if (isSkippable(f) || isRelation(f)) return false
        val t = f.type
        val isNumber = Number::class.java.isAssignableFrom(t) || (t.isPrimitive && t != Boolean::class.java)
        val isDate = java.time.temporal.Temporal::class.java.isAssignableFrom(t) || java.util.Date::class.java.isAssignableFrom(t)
        return isNumber || isDate || t == String::class.java || t.isEnum
    }

    private fun resolveDefaultSort(): String {
        val createNames = setOf("createdAt", "createdDate", "created_at")

        allFields.find { f -> f.name in createNames && isSortable(f) }?.let { return it.name }
        allFields.find { f -> f.name != idField && Number::class.java.isAssignableFrom(f.type) && isSortable(f) }?.let { return it.name }

        return idField
    }


}