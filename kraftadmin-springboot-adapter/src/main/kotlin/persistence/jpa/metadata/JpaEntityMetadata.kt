package persistence.jpa.metadata

import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.spi.KraftEntityMetadata
import jakarta.persistence.*
import org.hibernate.Hibernate
import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.annotation.CreatedDate
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Optimized metadata inspector for JPA entities.
 * All heavy reflection is performed once during initialization.
 */
class JpaEntityMetadata<T : Any>(
    private val entityClass: KClass<T>
) : KraftEntityMetadata<T>, JpaLazyInitializationSupport {

    private val logger = KraftAdminLogging.logger(javaClass)

    // Eagerly cache the class hierarchy and fields
    private val allFields: List<Field> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val startClass: Class<*> = entityClass.java

        generateSequence(startClass) { it.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.toList() }
            .onEach { it.isAccessible = true }
            .toList()
    }

    val versionField: Field? by lazy {
        allFields.firstOrNull {
            it.isAnnotationPresent(Version::class.java)
        }
    }

    override val versioningEnabled: Boolean
        get() = versionField != null

    //  Pre-filter fields by category for O(1) retrieval during runtime
    private val idFieldInternal: Field by lazy {
        allFields.find { it.isAnnotationPresent(Id::class.java) || it.isAnnotationPresent(org.springframework.data.annotation.Id::class.java) }
            ?: error("Entity ${entityClass.simpleName} must have a field annotated with @Id")
    }

    override val entityName: String by lazy {
        entityClass.java.getAnnotation(Entity::class.java)?.name?.takeIf { it.isNotBlank() }
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

    override val sortableFields: List<String> by lazy {
        allFields.filter { isSortable(it) && (it.getAnnotation(KraftAdminField::class.java)?.sortable ?: true) }
            .map { it.name }.distinct()
    }

    override val searchableFields: List<String> by lazy {
        allFields.filter { isSearchable(it) }
            .map { it.name }.take(5)
    }

    override val defaultSort: String by lazy { resolveDefaultSort() }

    // --- High-Performance Logic ---
    override fun convertId(idValue: Any?): Any? {
        val value = idValue?.toString() ?: return null
        return try {
            when (idType) {
                UUID::class.java -> UUID.fromString(value)
                Long::class.java, java.lang.Long::class.java -> value.toLong()
                Int::class.java, java.lang.Integer::class.java -> value.toInt()
                Short::class.java, java.lang.Short::class.java -> value.toShort()
                Byte::class.java, java.lang.Byte::class.java -> value.toByte()
                else -> value
            }
        } catch (e: Exception) { value }
    }

    override fun getIdentifier(entity: T): Any? {
        return idFieldInternal.get(entity)
    }

    // --- Private Helpers ---

    private fun isRelation(f: Field) = f.isAnnotationPresent(OneToOne::class.java) ||
            f.isAnnotationPresent(OneToMany::class.java) || f.isAnnotationPresent(ManyToOne::class.java) ||
            f.isAnnotationPresent(ManyToMany::class.java)

    private fun isSearchable(f: Field): Boolean {
        if (PropertyResolver.shouldSkip(f) || Modifier.isTransient(f.modifiers) || isRelation(f)) return false
        val admin = f.getAnnotation(KraftAdminField::class.java)
        val isTypeValid = f.type == String::class.java || f.type.isEnum
        return if (admin != null) admin.searchable && isTypeValid else isTypeValid
    }

    private fun isSortable(f: Field): Boolean {
        if (PropertyResolver.shouldSkip(f) || Modifier.isTransient(f.modifiers) || isRelation(f)) return false
        val t = f.type
        val isNumber = Number::class.java.isAssignableFrom(t) || (t.isPrimitive && t != Boolean::class.java)
        val isDate = java.time.temporal.Temporal::class.java.isAssignableFrom(t) || java.util.Date::class.java.isAssignableFrom(t)
        return isNumber || isDate || t == String::class.java || (t.isEnum && f.getAnnotation(Enumerated::class.java)?.value == EnumType.STRING)
    }

    private fun resolveDefaultSort(): String {
        val createAnnos = listOf(CreatedDate::class.java, CreationTimestamp::class.java)
        val createNames = setOf("createdAt", "createdDate", "created_at")

        allFields.find { f -> createAnnos.any { f.isAnnotationPresent(it) } && isSortable(f) }?.let { return it.name }
        allFields.find { f -> f.name in createNames && isSortable(f) }?.let { return it.name }
        allFields.find { f -> f.name != idField && Number::class.java.isAssignableFrom(f.type) && isSortable(f) }?.let { return it.name }

        return idField
    }

    /**
     * Forces initialization of ONLY @Lob fields. This exists to prevent
     * LazyInitializationException when a LOB is streamed/serialized AFTER
     * the transaction/session closes. It must never touch relation fields —
     * that was the source of a severe N+1 (one query per relation per row,
     * regardless of whether the relation is shown).
     */
    override fun ensureLobsInitialized(entity: Any) {
        entity::class.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach
            if (!field.isAnnotationPresent(Lob::class.java)) return@forEach

            try {
                field.isAccessible = true
                val value = field.get(entity)
                if (value != null) Hibernate.initialize(value)
            } catch (e: Exception) {
                logger.debug("Could not initialize LOB field ${field.name}: ${e.message}")
            }
        }
    }

    /**
     * Forces initialization of specific relation fields, by name, while the
     * session is still open. Used by FetchById (detail view) to resolve the
     * ManyToOne/OneToOne relations actually needed for that entity — never
     * called in list-view row mapping, and never touches @OneToMany/@ManyToMany
     * (those go through RelatedResourceFetcher's own bounded query instead).
     */
    override fun ensureSingleRelationsInitialized(entity: Any) {
        entity::class.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach

            val isSingleRelation = field.isAnnotationPresent(ManyToOne::class.java) ||
                    field.isAnnotationPresent(OneToOne::class.java)
            if (!isSingleRelation) return@forEach

            try {
                field.isAccessible = true
                val value = field.get(entity)
                if (value != null) Hibernate.initialize(value)
            } catch (e: Exception) {
                logger.debug("Could not initialize relation ${field.name}: ${e.message}")
            }
        }
    }
}