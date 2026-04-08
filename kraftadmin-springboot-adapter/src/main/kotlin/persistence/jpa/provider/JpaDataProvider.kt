package com.kraftadmin.persistence.jpa.provider

import api.responses.PagedResponse
import api.utils.EmbeddedResponse
import api.utils.ObjectResponse
import api.utils.ResourceRow
import api.utils.RowMetadata
import com.fasterxml.jackson.annotation.JsonIgnore
import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.config.SpringKraftAdminProperties
import com.kraftadmin.enums.KraftLogAction
import com.kraftadmin.utils.logging.KraftAdminAuditor
import com.kraftadmin.security.AdminPrincipal
import com.kraftadmin.security.SecurityProviderChain
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.spi.KraftDataProvider
import com.kraftadmin.ui_descriptors.LookupDescriptor
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.utils.telementary.KraftTelemetryEvent
import com.kraftadmin.utils.telementary.KraftTelemetryService
import com.kraftadmin.utils.telementary.TelemetryType
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Modifier
import java.util.*
import kotlin.jvm.Transient
import kotlin.jvm.java
import kotlin.jvm.javaClass
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField


class JpaDataProvider<T : Any>(
    private val entityManager: EntityManager,
    private val entityClass: KClass<T>,
    private val transactionTemplate: TransactionTemplate,
    private val adminStorageProvider: AdminStorageProvider? = null,
    private val kraftAdminAuditor: KraftAdminAuditor,
//    private val adminSecurityProvider: AdminSecurityProvider,
    private val securityChain: SecurityProviderChain,
    private val properties: SpringKraftAdminProperties,
    private val telemetryService: KraftTelemetryService
) : KraftDataProvider<T> {

    val logger = LoggerFactory.getLogger(KraftDataProvider::class.java)

    private fun ensureLobsInitialized(entity: Any) {
        entity::class.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach
            if (field.isAnnotationPresent(OneToOne::class.java) ||
                field.isAnnotationPresent(ManyToOne::class.java) ||
                field.isAnnotationPresent(Lob::class.java)) {
                field.isAccessible = true
                val v = field.get(entity)
                if (v != null) Hibernate.initialize(v)
            }
        }
    }


    private fun ensureLobsInitialized1(entity: Any?) {
        if (entity == null) return

        // Scan for @Lob fields and "touch" them to trigger the stream load
        entity.javaClass.declaredFields.forEach { field ->
            // Using standard reflection check or your KraftAnnotationUtils
            if (field.isAnnotationPresent(jakarta.persistence.Lob::class.java)) {
                try {
                    field.isAccessible = true
                    val value = field.get(entity)
                    // Just accessing the value (calling toString or length)
                    // triggers the OID streaming while the transaction is open.
                    value?.toString()
                } catch (e: Exception) {
                    logger.warn("Could not pre-initialize LOB field: ${field.name}")
                }
            }
        }
    }

    override fun fetchAll(
        page: Int,
        size: Int,
        columns: List<KraftAdminColumn>
    ): PagedResponse<ResourceRow> {
        val config = properties.pagination
        val effectivePage = page.coerceAtLeast(1)
        val limit = size.coerceAtLeast(1).coerceAtMost(config.maxPageSize)
        val offset = (effectivePage - 1) * limit

        val response = transactionTemplate.execute { status ->
            try {
                val cb = entityManager.criteriaBuilder

                // --- 1. Total Count ---
                val countQuery = cb.createQuery(Long::class.java)
                countQuery.select(cb.count(countQuery.from(entityClass.java)))
                val total = entityManager.createQuery(countQuery).singleResult

                // --- 2. Build the Data Query ---
                val selectQuery = cb.createQuery(entityClass.java)
                val root = selectQuery.from(entityClass.java)

                // DYNAMIC SORTING
                val sortField = findBestSortField()
                if (sortField != null) {
                    // CRITICAL: This MUST happen before createQuery()
                    selectQuery.orderBy(cb.desc(root.get<Any>(sortField)))
                }

                // --- 3. Create the Executable TypedQuery ---
                val typedQuery = entityManager.createQuery(selectQuery)

                typedQuery.firstResult = offset
                typedQuery.maxResults = limit

                val rows = typedQuery.resultList.map { entity ->
                    ensureLobsInitialized(entity)
                    // Unproxy the root entity
                    val realEntity = unproxy(entity) ?: entity
                    mapToRow(realEntity)
                }

                val totalPages = if (total == 0L) 0 else Math.ceil(total.toDouble() / limit).toInt()

                PagedResponse(rows, total, effectivePage, limit, totalPages)
            } catch (e: Exception) {
                logger.error("DB Pagination Error: ${e.message}", e)
                status.setRollbackOnly()
                PagedResponse(emptyList(), 0, effectivePage, limit, 0)
            }
        }
//            ?: PagedResponse(emptyList(), 0, effectivePage, limit, 0)

        return response!!
    }

    /**
     * Finds the field name annotated with common creation timestamp annotations.
     */
    private val cachedSortField: String? by lazy {
        findFieldInHierarchy(entityClass.java)
    }

    private fun findFieldInHierarchy(clazz: Class<*>): String? {
        logger.info("finding best sort field for ${entityClass.java.simpleName}")

        val fieldName = clazz.declaredFields.find { field ->
            field.isAnnotationPresent(org.hibernate.annotations.CreationTimestamp::class.java) ||
                    field.isAnnotationPresent(org.springframework.data.annotation.CreatedDate::class.java) ||
                    field.name == "createdAt" ||
                    field.name == "createdDate"
        }?.name

        if (fieldName != null) return fieldName

        //  If not found and there's a superclass, recurse
        val superclass = clazz.superclass
        if (superclass != null && superclass != Any::class.java) {
            return findFieldInHierarchy(superclass)
        }

        return null
    }

    private fun findBestSortField(): String? = cachedSortField

    // fetch resource using its id
//    override fun fetchById(id: String, columns: List<KraftAdminColumn>): ResourceRow? {
//        val startTime = System.currentTimeMillis()
//        // Ensure we are in a read-only transaction context
//        return transactionTemplate.execute { status ->
//            try {
//                val entity = entityManager.find(entityClass.java, convertId(id))
//
//                if (entity != null) {
//                    // 1. Force LOB loading while the transaction is open
//                    ensureLobsInitialized(entity)
//
//                    // 2. Map to row while the transaction is open
//                    mapToRow(entity)
//                } else null
//            } catch (e: Exception) {
//                logger.error("Error fetching resource by ID: ${e.message}", e)
//                null
//            }
//        }
//    }

    override fun fetchById(id: String, columns: List<KraftAdminColumn>): ResourceRow? {
        return transactionTemplate.execute { status ->
            try {
                val entity = entityManager.find(entityClass.java, convertId(id))

                if (entity != null) {
                    ensureLobsInitialized(entity)
                    // Unproxy associations before mapping — same protection as fetchAll
                    mapToRow(unproxy(entity) ?: entity)
                } else null
            } catch (e: Exception) {
                logger.error("Error fetching resource by ID: ${e.message}", e)
                null
            }
        }
    }

    override fun delete(id: String) {
        transactionTemplate.execute { status ->
            try {
                val convertedId = convertId(id)
                val entity = entityManager.find(entityClass.java, convertedId)

                if (entity != null) {
                    cleanupEntityFiles(entity)

                    //  Record the audit BEFORE the delete (while we still have the entity)
                    kraftAdminAuditor.record(
                        action = KraftLogAction.DELETE,
                        resource = entityClass.simpleName ?: "Unknown",
                        id = id,
                        actor = getCurrentUser()
                    )

                    entityManager.remove(entity)
                    // Explicitly flush if you want to catch constraint violations
                    // (like foreign key errors) within this block
                    entityManager.flush()
                    logger.info("Deleted ${entityClass.simpleName} with id: $id")
                } else {
                    logger.warn("Delete skipped: ${entityClass.simpleName} with id $id not found")
                }
            } catch (e: Exception) {
                logger.error("❌ Delete failed for $id: ${e.message}")
                status.setRollbackOnly() // Ensure the transaction rolls back on failure
                throw e
            }
        }
    }

    /**
     * Scans the entity for String fields containing KraftAdmin file paths
     * and tells the storage provider to wipe them.
     */
    private fun cleanupEntityFiles(entity: Any) {
        // Only proceed if a storage provider was actually injected
        adminStorageProvider?.let { provider ->
            entity::class.java.declaredFields.forEach { field ->
                if (field.type == String::class.java) {
                    field.isAccessible = true
                    val value = field.get(entity) as? String

                    if (value != null && (value.startsWith("/admin/files/") || value.contains("cloudinary.com"))) {
                        provider.delete(value)
                    }
                }
            }
        }
    }

/*
* Save item to db
*
 */
    override fun save(name: String, data: Map<String, Any?>): Map<String, Any?> {
        return transactionTemplate.execute<Map<String, Any?>> { status ->
            val data = (data["data"] as? Map<String, Any?>) ?: data
            val rawId = data["id"] ?: data["ID"]


            // Identify if we are creating or updating
            val isNew = (rawId == null || rawId.toString().isBlank())

            val entity: T = if (!isNew) {
                val idType = entityManager.entityManagerFactory.metamodel
                    .entity(entityClass.java)
                    .idType.javaType

                val convertedId = try {
                    when (idType) {
                        java.util.UUID::class.java -> java.util.UUID.fromString(rawId.toString())
                        java.lang.Long::class.java -> rawId.toString().toLong()
                        java.lang.Integer::class.java -> rawId.toString().toInt()
                        else -> rawId
                    }
                } catch (e: Exception) {
                    logger.warn("ID conversion failed for $rawId, using raw.")
                    rawId
                }
                entityManager.find(entityClass.java, convertedId) ?: createNewInstance()
            } else {
                createNewInstance()
            }

            // Sanitize payload (don't manually overwrite audit timestamps)
            val updateableData = data.filterKeys {
                it.lowercase() !in listOf("id", "createdat", "updatedat", "created_at", "updated_at")
            }

            applyDataToEntity(entity, updateableData)

            // Persist to DB
            val managedEntity = entityManager.merge(entity)
            entityManager.flush()

            // Get the final ID (crucial for NEW entities where ID was generated on flush)
            val finalId = getEntityId(managedEntity).toString()

            // Record the Audit
            kraftAdminAuditor.record(
                action = if (isNew) KraftLogAction.CREATE else KraftLogAction.UPDATE,
                resource = entityClass.simpleName ?: "Unknown",
                id = finalId,
                actor = getCurrentUser()
            )

            mapEntityToData(managedEntity)

        } ?: emptyMap()
    }

    /**
     * Helper to extract ID from a managed entity using JPA metadata
     */
    private fun getEntityId(entity: T): Any? {
        return entityManager.entityManagerFactory.persistenceUnitUtil.getIdentifier(entity)
    }

    /**
     * Extracted helper to keep the save logic clean
     */
    private fun createNewInstance(): T {
        return try {
            val constructor = entityClass.java.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance() as T
        } catch (e: Exception) {
            val constructor = entityClass.java.constructors.first()
            val args: Array<Any?> = Array(constructor.parameterCount) { null }
            constructor.newInstance(*args) as T
        }
    }

    /**
     * Recursively applies map data to an entity or an @Embedded object.
     */
    private fun applyDataToEntity(target: Any, data: Map<String, Any?>) {
        val targetClass = target::class

        data.forEach { (key, value) ->
            if (key == "id" || value == null) return@forEach

            val prop = targetClass.memberProperties.find { it.name == key }
            if (prop is KMutableProperty<*>) {
                prop.isAccessible = true
                val field = prop.javaField ?: return@forEach
                val classifier = prop.returnType.classifier as? KClass<*>

                try {
                    when {
                        // 1. COLLECTIONS (List, Set, etc.)
                        field.isAnnotationPresent(ElementCollection::class.java) ||
                                field.isAnnotationPresent(ManyToMany::class.java) ||
                                field.isAnnotationPresent(OneToMany::class.java) -> {

                            var currentCollection = prop.getter.call(target) as? MutableCollection<Any>
                            if (currentCollection == null) {
                                currentCollection = if (classifier?.isSubclassOf(Set::class) == true) mutableSetOf() else mutableListOf()
                                prop.setter.call(target, currentCollection)
                            }
                            currentCollection.clear()

                            val items = when (value) {
                                is Collection<*> -> value
                                is String -> value.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                else -> listOf(value)
                            }

                            val genericType = prop.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                            items.forEach { item ->
                                if (item == null) return@forEach

                                val valueToAdd = when {
                                    // Entities
                                    field.isAnnotationPresent(ManyToMany::class.java) || field.isAnnotationPresent(OneToMany::class.java) -> {
                                        val id = if (item is Map<*, *>) item["id"] ?: item["lookupKey"] else item
                                        val ref = entityManager.getReference(genericType!!.java, convertIdForClass(genericType, id))
                                        if (field.isAnnotationPresent(OneToMany::class.java)) setBackReference(ref, target)
                                        ref
                                    }
                                    // Embeddables
//                                    genericType?.isAnnotationPresent(Embeddable::class.java) == true && item is Map<*, *> -> {
//                                        val inst = genericType.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
//                                        applyDataToEntity(inst, item as Map<String, Any?>)
//                                        inst
//                                    }
                                    // NEW: Enums inside a collection
                                    genericType != null && genericType.isSubclassOf(Enum::class) -> {
                                        genericType.java.enumConstants.filterIsInstance<Enum<*>>().find { it.name.equals(item.toString(), true) }
                                    }
                                    // Simple Types
                                    else -> coerceValue(item, genericType)
                                }
                                if (valueToAdd != null) currentCollection.add(valueToAdd)
                            }
                        }

                        // 2. MAPS (e.g. @ElementCollection Map<String, String>)
                        classifier != null && classifier.isSubclassOf(Map::class) && value is Map<*, *> -> {
                            val currentMap = prop.getter.call(target) as? MutableMap<Any, Any>
                            if (currentMap != null) {
                                currentMap.clear()
                                currentMap.putAll(value as Map<out Any, Any>)
                            }
                        }

                        // 3. EMBEDDED SINGLE OBJECT
                        field.isAnnotationPresent(Embedded::class.java) && value is Map<*, *> -> {
                            var inst = prop.getter.call(target)
                            if (inst == null) {
                                inst = (classifier as KClass<*>).java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                                prop.setter.call(target, inst)
                            }
                            applyDataToEntity(inst!!, value as Map<String, Any?>)
                        }

                        // 4. SCALAR FIELDS
                        else -> {
                            val convertedValue = when {
                                // Relational ID
                                field.isAnnotationPresent(ManyToOne::class.java) || field.isAnnotationPresent(OneToOne::class.java) -> {
                                    val id = if (value is Map<*, *>) value["id"] ?: value["lookupKey"] else value
                                    if (id?.toString()?.isNotBlank() == true) {
                                        val relatedClass = classifier as KClass<*>
                                        entityManager.getReference(relatedClass.java, convertIdForClass(relatedClass, id))
                                    } else null
                                }
                                // Dates (JSR-310)
                                classifier == java.time.LocalDateTime::class -> parseDateTime(value)
                                classifier == java.time.LocalDate::class -> java.time.LocalDate.parse(value.toString().substringBefore("T"))
                                classifier == java.time.ZonedDateTime::class -> java.time.ZonedDateTime.parse(value.toString())

                                // Time
                                // Handle LocalTime from <input type="time">
                                classifier == java.time.LocalTime::class -> {
                                    val raw = value.toString()
                                    when {
                                        // If it's a full ISO string (from JS Date.toISOString())
                                        raw.contains("T") -> java.time.LocalTime.parse(raw.substringAfter("T").substringBefore("Z"))
                                        // If it's just "HH:mm" (Standard HTML5 time input)
                                        raw.length == 5 -> java.time.LocalTime.parse("$raw:00")
                                        // Already "HH:mm:ss"
                                        else -> java.time.LocalTime.parse(raw)
                                    }
                                }
                                // Enums
                                classifier != null && classifier.isSubclassOf(Enum::class) -> {
                                    classifier.java.enumConstants.filterIsInstance<Enum<*>>().find { it.name.equals(value.toString(), true) }
                                }
                                // UUID
                                classifier == UUID::class && value is String -> UUID.fromString(value)
                                // Booleans (Handle "true", "on", 1)
                                classifier == Boolean::class -> value.toString().lowercase().let { it == "true" || it == "on" || it == "1" }
                                // Numbers
                                classifier != null && classifier.isSubclassOf(Number::class) -> coerceValue(value, classifier)
                                else -> value
                            }
                            prop.setter.call(target, convertedValue)
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Mapping failed for '$key' in ${targetClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    private fun parseDateTime(value: Any?): java.time.LocalDateTime? {
        val str = value?.toString() ?: return null
        if (str.isBlank()) return null
        return try {
            // Handle ISO with T or space
            java.time.LocalDateTime.parse(str.replace(" ", "T"))
        } catch (e: Exception) {
            // Fallback for date-only strings
            java.time.LocalDate.parse(str).atStartOfDay()
        }
    }

    /**
     * For OneToMany, we need to find the field in the child that points back to the parent
     * and set it so JPA knows the relationship is owned.
     */
    private fun setBackReference(child: Any, parent: Any) {
        val childClass = child::class
        val parentClass = parent::class

        val backRefProp = childClass.memberProperties.find { prop ->
            val classifier = prop.returnType.classifier as? KClass<*>
            classifier != null && parentClass.isSubclassOf(classifier) &&
                    prop.javaField?.isAnnotationPresent(ManyToOne::class.java) == true
        }

        if (backRefProp is KMutableProperty<*>) {
            backRefProp.isAccessible = true
            backRefProp.setter.call(child, parent)
        }
    }

    /**
     * Coerces simple types for ElementCollections (e.g. String to Double)
     */
    private fun coerceValue(value: Any, targetType: KClass<*>?): Any {
        return when (targetType) {
            Double::class -> value.toString().toDouble()
            Int::class -> value.toString().toInt()
            Long::class -> value.toString().toLong()
            else -> value
        }
    }

    override fun getLookupData(
        lookup: LookupDescriptor,
        limit: Int,
        searchQuery: String?
    ): List<ObjectResponse> {
        logger.info("lookup $lookup")
        val cb = entityManager.criteriaBuilder
        val javaClass = entityClass.java as Class<Any>
        val query = cb.createQuery(javaClass)
        val root = query.from(javaClass)

        if (!searchQuery.isNullOrBlank()) {
            query.where(
                cb.like(
                    cb.lower(root.get<String>(lookup.searchField)), // ← use lookup.searchField directly
                    "%${searchQuery.lowercase()}%"
                )
            )
        }

        return entityManager.createQuery(query.select(root))
            .setMaxResults(limit)
            .resultList
            .map { entity ->
                ObjectResponse(
                    id = extractId(entity).toString(),
                    displayField = resolveDisplayLabel(entity) ?: "Unknown"
                )
            }
    }

    override fun countAll(name: String): Long? {
            return transactionTemplate.execute {
                val cb = entityManager.criteriaBuilder
                val query = cb.createQuery(Long::class.java)
                query.select(cb.count(query.from(entityClass.java)))
                entityManager.createQuery(query).singleResult
            } ?: 0L
    }

    private fun mapEntityToData(entity: Any?): Map<String, Any?> {
        if (entity == null) return emptyMap()
        val result = mutableMapOf<String, Any?>()
        val kClass = entity::class

        kClass.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach

            //  Also check @Transient on the getter method (covers @Transient on methods like getAuthor())
            val getterMethod = try {
                entity::class.java.getMethod("get${prop.name.replaceFirstChar { it.uppercase() }}")
            } catch (e: NoSuchMethodException) { null }

            val isTransientField = field.isAnnotationPresent(Transient::class.java) == true ||
                    field.isAnnotationPresent(jakarta.persistence.Transient::class.java) == true

            val isTransientMethod = getterMethod?.isAnnotationPresent(Transient::class.java) == true ||
                    getterMethod?.isAnnotationPresent(jakarta.persistence.Transient::class.java) == true

            // ✅ Also skip if there's no backing field at all (computed/derived properties)
            val hasNoBackingField = field == null

            if (isTransientField || isTransientMethod || hasNoBackingField ||
                Modifier.isStatic(field.modifiers ?: 0) ||
                field.isAnnotationPresent(JsonIgnore::class.java) == true
            ) {
                return@forEach
            }

//            // 1. Skip non-persistent or ignored fields
//            if (field.isAnnotationPresent(Transient::class.java) ||
//                Modifier.isStatic(field.modifiers) ||
//                field.isAnnotationPresent(jakarta.persistence.Transient::class.java) ||
//                field.isAnnotationPresent(JsonIgnore::class.java)
//            ) {
//                return@forEach
//            }

            val rawValue = try {
                field.isAccessible = true
                field.get(entity)
            } catch (e: Exception) {
                logger.warn("Could not read field ${field.name}: ${e.message}")
                null
            }

            // ✅ Unproxy before inspection — handles ByteBuddy/CGLIB Hibernate proxies
            val value = unproxy(rawValue)

            when {
                // 2. Handle Embedded (Value Objects)
                field.isAnnotationPresent(Embedded::class.java) -> {
                    if (value == null) {
                        result[prop.name] = null
                    } else {
                        val fullMap = mapEntityToData(value)
                        val summary = fullMap.values
                            .filterIsInstance<String>()
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString(", ")

                        result[prop.name] = EmbeddedResponse(summary, fullMap)
                    }
                }

                // 3. Handle Relationships (ManyToOne / OneToOne)
//                field.isAnnotationPresent(ManyToOne::class.java) ||
//                        field.isAnnotationPresent(OneToOne::class.java) -> {
//                    if (value == null) {
//                        result[prop.name] = null
//                    } else {
//                        val id = extractId(value).toString()
//
//                        val label = try {
//                            val relationProps = value::class.memberProperties
//
//                            val potentialLabels = relationProps.filter { p ->
//                                val name = p.name.lowercase()
//                                name != "id" &&
//                                        !name.endsWith("id") &&
//                                        !name.contains("password") &&
//                                        isSimpleType(p.returnType.classifier as KClass<*>)
//                            }
//
//                            val bestField =
//                                potentialLabels.find { it.name == "name" || it.name == "title" || it.name == "label" }
//                                    ?: potentialLabels.firstOrNull()
//
//                            bestField?.getter?.call(value)?.toString()
//                        } catch (e: Exception) {
//                            null
//                        } ?: id
//
//                        result[prop.name] = ObjectResponse(id, label)
//                    }
//                }

                // 3. Handle Relationships (ManyToOne / OneToOne)
                field.isAnnotationPresent(ManyToOne::class.java) ||
                        field.isAnnotationPresent(OneToOne::class.java) -> {
                    if (value == null) {
                        result[prop.name] = null
                    } else {
                        val id = try { extractId(value).toString() } catch (e: Exception) { null }

                        if (id == null) {
                            result[prop.name] = null
                        } else {
                            val label = try {
                                value::class.memberProperties
                                    .filter { p ->
                                        val javaField = p.javaField ?: return@filter false
                                        // Skip transient, static, ignored fields on the related entity
                                        if (javaField.isAnnotationPresent(Transient::class.java) ||
                                            javaField.isAnnotationPresent(jakarta.persistence.Transient::class.java) ||
                                            Modifier.isStatic(javaField.modifiers)) return@filter false

                                        val name = p.name.lowercase()
                                        if (name == "id" || name.endsWith("id") || name.contains("password")) return@filter false

                                        // ✅ Safe classifier check — skip if not a plain KClass
                                        val classifier = p.returnType.classifier
                                        if (classifier !is KClass<*>) return@filter false

                                        isSimpleType(classifier)
                                    }
                                    .let { candidates ->
                                        candidates.find { it.name == "name" || it.name == "title" || it.name == "label" }
                                            ?: candidates.firstOrNull()
                                    }
                                    ?.let { best ->
                                        best.isAccessible = true
                                        best.getter.call(value)?.toString()
                                    }
                            } catch (e: Exception) {
                                logger.warn("Could not resolve label for ${value::class.simpleName}.${prop.name}: ${e.message}")
                                null
                            } ?: id

                            result[prop.name] = ObjectResponse(id, label)
                        }
                    }
                }

                // 4. Handle Collections
                value is Collection<*> -> {
                    result[prop.name] = value.map { item ->
                        val realItem = unproxy(item) // ✅ Unproxy each collection element
                        if (realItem == null) null
                        else if (isSimpleType(realItem::class)) realItem
                        else extractId(realItem).toString()
                    }
                }

                // 5. Simple Types
                else -> result[prop.name] = value
            }
        }
        return result
    }


//    private fun mapEntityToData(entity: Any?): Map<String, Any?> {
//        if (entity == null) return emptyMap()
//        val result = mutableMapOf<String, Any?>()
//        val kClass = entity::class
//
//        kClass.memberProperties.forEach { prop ->
//            val field = prop.javaField ?: return@forEach
//
//            // 1. Skip non-persistent or ignored fields
//            if (field.isAnnotationPresent(Transient::class.java) ||
//                Modifier.isStatic(field.modifiers) ||
//                field.isAnnotationPresent(jakarta.persistence.Transient::class.java) ||
//                field.isAnnotationPresent(JsonIgnore::class.java)
//            ) {
//                return@forEach
//            }
//
//            val value = try {
//                field.isAccessible = true
//                field.get(entity)
//            } catch (e: Exception) {
//                logger.warn("Could not read field ${field.name}: ${e.message}")
//                null
//            }
//
//            when {
//                // 2. Handle Embedded (Value Objects)
//                field.isAnnotationPresent(Embedded::class.java) -> {
//                    if (value == null) {
//                        result[prop.name] = null
//                    } else {
//                        val fullMap = mapEntityToData(value)
//                        // Take the first 2 string values for a readable summary
//                        val summary = fullMap.values
//                            .filterIsInstance<String>()
//                            .filter { it.isNotBlank() }
//                            .take(2)
//                            .joinToString(", ")
//
//                        result[prop.name] = EmbeddedResponse(summary, fullMap)
//                    }
//                }
//
//                // 2. Handle Relationships (ManyToOne / OneToOne)
//                field.isAnnotationPresent(ManyToOne::class.java) ||
//                        field.isAnnotationPresent(OneToOne::class.java) -> {
//                    if (value == null) {
//                        result[prop.name] = null
//                    } else {
//                        val id = extractId(value).toString()
//
//                        val label = try {
//                            // 1. Get all properties of the related entity
//                            val relationProps = value::class.memberProperties
//
//                            // 2. Filter out IDs, Passwords, and Internal JPA fields
//                            val potentialLabels = relationProps.filter { p ->
//                                val name = p.name.lowercase()
//                                name != "id" &&
//                                        !name.endsWith("id") &&
//                                        !name.contains("password") &&
//                                        isSimpleType(p.returnType.classifier as KClass<*>)
//                            }
//
//                            // 3. Try to find common naming conventions first
//                            val bestField =
//                                potentialLabels.find { it.name == "name" || it.name == "title" || it.name == "label" }
//                                    ?: potentialLabels.firstOrNull() // Fallback to the first non-ID simple field
//
//                            bestField?.getter?.call(value)?.toString()
//                        } catch (e: Exception) {
//                            null
//                        } ?: id // Final fallback to ID if the entity is JUST an ID
//
//                        result[prop.name] = ObjectResponse(id, label)
//                    }
//                }
//
//                // 4. Handle Collections (New additions for completeness)
//                value is Collection<*> -> {
//                    result[prop.name] = value.map { item ->
//                        if (item == null) null
//                        else if (isSimpleType(item::class)) item
//                        else {
//                            // For collections of entities, we just return IDs for now to avoid massive JSON
//                            extractId(item).toString()
//                        }
//                    }
//                }
//
//                // 5. Simple Types
//                else -> result[prop.name] = value
//            }
//        }
//        return result
//    }

    private fun mapEntityToData1(entity: Any?): Map<String, Any?> {
        if (entity == null) return emptyMap()

        // 1. Force Unproxy the root
        val cleanEntity = org.hibernate.Hibernate.unproxy(entity)
        val result = mutableMapOf<String, Any?>()
        val javaClass = cleanEntity.javaClass

        javaClass.declaredFields.forEach { field ->
            // 2. HARD FILTER: If the field name starts with $ or hibernate, SKIP IT
            if (field.name.startsWith("$") ||
                field.name.startsWith("hibernate") ||
                Modifier.isStatic(field.modifiers) ||
                field.isSynthetic) return@forEach

            // 3. Normal JPA/Jackson Ignores
            if (field.isAnnotationPresent(Transient::class.java) ||
                field.isAnnotationPresent(jakarta.persistence.Transient::class.java) ||
                field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonIgnore::class.java)) {
                return@forEach
            }

            val rawValue = try {
                field.isAccessible = true
                field.get(cleanEntity)
            } catch (e: Exception) { null }

            // 4. Force Unproxy the value
            val value = if (rawValue != null) org.hibernate.Hibernate.unproxy(rawValue) else null

            when {
                // RELATIONS
                field.isAnnotationPresent(ManyToOne::class.java) ||
                        field.isAnnotationPresent(OneToOne::class.java) -> {
                    if (value == null) {
                        result[field.name] = null
                    } else {
                        val id = extractId(value).toString()
                        val label = resolveDisplayLabel(value) ?: id
                        result[field.name] = ObjectResponse(id, label)
                    }
                }

                // EMBEDDED
                field.isAnnotationPresent(Embedded::class.java) -> {
                    result[field.name] = if (value == null) null else mapEntityToData(value)
                }

                // COLLECTIONS
                value is Collection<*> -> {
                    result[field.name] = value.map { item ->
                        if (item == null) null
                        else {
                            val cleanItem = org.hibernate.Hibernate.unproxy(item)
                            if (isSimpleType(cleanItem::class.java)) cleanItem
                            else extractId(cleanItem).toString()
                        }
                    }
                }

                // 5. THE ULTIMATE ELSE SAFETY
                else -> {
                    if (value == null) {
                        result[field.name] = null
                    } else if (isSimpleType(value::class.java)) {
                        result[field.name] = value
                    } else {
                        // If we reach here, it's a complex object but NO relationship
                        // annotation was found. This is where the ByteBuddy leaks.
                        // DO NOT put the object in.
                        result[field.name] = value.toString()
                    }
                }
            }
        }
        return result
    }


    /**
     * Helper to check if we can safely pass the value to JSON without recursion
     */
    private fun isSimpleType(kClass: KClass<*>): Boolean {
        return kClass.java.isPrimitive ||
                kClass == String::class ||
                kClass == Boolean::class ||
                kClass == Number::class ||
                kClass.java.isEnum ||
                kClass.simpleName?.contains("LocalDate") == true
    }

    private fun isSimpleType1(kClass: KClass<*>): Boolean {
        val typeName = kClass.java.name

        // If the class name contains ByteBuddy or Hibernate, it is NOT simple.
        if (typeName.contains("Hibernate") ||
            typeName.contains("ByteBuddy") ||
            typeName.contains("_$$")) return false

        return kClass.java.isPrimitive ||
                typeName.startsWith("java.lang") ||
                typeName.startsWith("java.time") ||
                typeName.startsWith("java.math") ||
                kClass.java.isEnum ||
                typeName == "java.util.UUID"
    }

    private fun isSimpleType(value: Any): Boolean {
        return value is String || value is Number || value is Boolean ||
                value is Enum<*> || value is java.time.temporal.Temporal ||
                value.javaClass.isPrimitive
    }

    private fun extractId1(entity: Any): Any? {
        val idProp = entity::class.memberProperties
            .find { it.javaField?.isAnnotationPresent(Id::class.java) == true || it.name == "id" }

        val field = idProp?.javaField ?: return null
        return try {
            field.isAccessible = true
            field.get(entity)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractId(entity: Any): Any? {
        val clean = unproxy(entity)
        // Use javaClass.fields to ensure we find the ID even on enhanced classes
        val idField = clean?.javaClass?.declaredFields?.find {
            it.isAnnotationPresent(Id::class.java) || it.name == "id"
        }
        idField?.isAccessible = true
        return idField?.get(clean)
    }

    /**
     * Converts a string from the URL into the actual type required by the JPA Entity
     */
    fun convertId(id: String): Any {
        // Find the field marked with @Id
        val idProperty = entityClass.memberProperties.find {
            it.javaField?.isAnnotationPresent(Id::class.java) == true
        } ?: return id // Fallback to string if no @Id found (unlikely in JPA)

        val targetType = idProperty.returnType.classifier

        return when (targetType) {
            UUID::class -> UUID.fromString(id)
            Long::class -> id.toLong()
            Int::class -> id.toInt()
            else -> id // Default to String
        }
    }

    /**
     * Entry point: Converts a JPA Entity into a structured ResourceRow.
     */
    fun mapToRow(entity: Any): ResourceRow {
        val id = extractId(entity).toString()
        val values = mapEntityToValues(entity)

        return ResourceRow(
            id = id,
            values = values,
            metadata = RowMetadata(
                canEdit = true, // You can later inject logic here to check roles
                canDelete = true
            )
        )
    }

    /**
     * The internal recursive mapper that builds the values map.
     */
    private fun mapEntityToValues(entity: Any?): Map<String, Any?> {
        if (entity == null) return emptyMap()
        val result = mutableMapOf<String, Any?>()
        val kClass = entity::class

        kClass.memberProperties.forEach { prop ->
            val field = prop.javaField ?: return@forEach

            // 1. Skip non-persistent or ignored fields
            if (field.isAnnotationPresent(Transient::class.java) ||
                Modifier.isStatic(field.modifiers) ||
                field.isAnnotationPresent(jakarta.persistence.Transient::class.java)
            ) {
                return@forEach
            }

            val rawValue = try {
                field.isAccessible = true
                field.get(entity)
            } catch (e: Exception) {
                null
            }

            // ✅ Unproxy before inspection — handles ByteBuddy/CGLIB proxies
            val value = unproxy(rawValue)

            when {
                // 2. Handle Embedded (Value Objects)
                field.isAnnotationPresent(Embedded::class.java) -> {
                    if (value == null) {
                        result[prop.name] = null
                    } else {
                        val fullMap = mapEntityToValues(value) // Recursive call
                        val summary = fullMap.values
                            .filterIsInstance<String>()
                            .filter { it.isNotBlank() }
                            .take(2)
                            .joinToString(", ")

                        result[prop.name] = EmbeddedResponse(summary, fullMap)
                    }
                }


                // 3. Handle Single Relationships
                field.isAnnotationPresent(ManyToOne::class.java) || field.isAnnotationPresent(OneToOne::class.java) -> {
                    if (value == null) {
                        result[prop.name] = null
                    } else {
                        val id = extractId(value).toString()
                        val label = resolveDisplayLabel(value) ?: id
                        result[prop.name] = ObjectResponse(id, label)
                    }
                }

                // 4. NEW: Handle Element Collections (Tags, Highlights, etc.)
                field.isAnnotationPresent(ElementCollection::class.java) -> {
                    result[prop.name] = if (value is Collection<*>) {
                        value.map { it?.toString() } // Return raw strings
                    } else {
                        emptyList<String>()
                    }
                }

                // 5. Handle Collection Relationships (ManyToMany / OneToMany)
                field.isAnnotationPresent(ManyToMany::class.java) || field.isAnnotationPresent(OneToMany::class.java) -> {
                    result[prop.name] = if (value is Collection<*>) {
                        value.map { item ->
                            if (item == null) null
                            else {
                                val id = extractId(item).toString()
                                val label = resolveDisplayLabel(item) ?: id
                                ObjectResponse(id, label) // Wrap them for the UI chips
                            }
                        }
                    } else emptyList<Any>()
                }

                // 6. Primitive / Simple types
                else -> result[prop.name] = value
            }
        }
        return result
    }

    /**
     * Helper to find a "Name" or "Title" field in a related entity.
     */
    private fun resolveDisplayLabel(entity: Any): String? {
        // 1. Handle Hibernate Proxies
        // We get the actual class, even if it's a proxy, so reflection finds the fields
        val actualEntity = if (entity is HibernateProxy) {
            entity.hibernateLazyInitializer.implementation
        } else {
            entity
        }

        val kClass = actualEntity::class
        val props = kClass.memberProperties

        //  Logic remains the same, but we operate on the unproxied 'actualEntity'
        val targetProp = props.find { prop ->
            val field = prop.javaField
            field?.isAnnotationPresent(com.kraftadmin.annotations.KraftAdminField::class.java) == true &&
                    field.getAnnotation(com.kraftadmin.annotations.KraftAdminField::class.java).displayField
        } ?: props.find {
            it.name.lowercase() in listOf("name", "title", "label", "username", "displayname")
        } ?: props.filter { prop ->
            val classifier = prop.returnType.classifier as? KClass<*>
            classifier != null && isSimpleType(classifier) && prop.name.lowercase() != "id"
        }.getOrNull(0)

        return try {
            val field = targetProp?.javaField
            if (field != null) {
                field.isAccessible = true
                field.get(actualEntity)?.toString()
            } else {
                targetProp?.getter?.call(actualEntity)?.toString()
            }
        } catch (e: Exception) {
            null
        } ?: "Unknown ${kClass.simpleName?.removeSuffix("\$HibernateProxy")}"
    }

    /**
     * Handles converting incoming UI data (Strings or Lists) into the correct Entity collection type.
     */
    private fun handleCollectionMapping(prop: KMutableProperty<*>, value: Any?): Any? {
        // Get the generic type of the List (e.g., String in List<String>)
        val typeArgument = prop.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>

        val listValues = when (value) {
            is String -> value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            is Collection<*> -> value.toList()
            else -> emptyList<Any>()
        }

        // If the target is List<String>, we are done.
        // If it's List<Long>, we'd convert here.
        return if (typeArgument == String::class) {
            listValues.map { it.toString() }
        } else {
            listValues // Fallback
        }
    }

    /**
     * Generic helper to convert a raw value (usually String) into the ID type
     * required by a specific entity class.
     */
    private fun convertIdForClass(targetKClass: KClass<*>, idValue: Any?): Any? {
        if (idValue == null) return null
        val idString = idValue.toString()

        // Find the @Id property of the RELATED class, not the root entityClass
        val idProperty = targetKClass.memberProperties.find {
            it.javaField?.isAnnotationPresent(Id::class.java) == true || it.name == "id"
        } ?: return idString

        return when (idProperty.returnType.classifier) {
            UUID::class -> UUID.fromString(idString)
            Long::class -> idString.toLong()
            Int::class -> idString.toInt()
            else -> idString
        }
    }

    /**
     * Unwraps a Hibernate proxy to its real underlying entity.
     * Safe to call on non-proxy objects too.
     */
    private fun unproxy(value: Any?): Any? {
        if (value == null) return null
        return if (value is HibernateProxy) {
            val lazyInitializer = value.hibernateLazyInitializer
            // This forces initialization and returns the real object
            lazyInitializer.implementation
        } else {
            value
        }
    }

    fun getCurrentUser() : AdminUserDTO {
//        return adminSecurityProvider.getCurrentUser()!!
        val adminDto = securityChain.resolveCurrentUser()!!
        logger.info("Current authenticated user $adminDto")
        return adminDto
    }

}
