package com.kraftadmin.discovery

import ch.qos.logback.core.pattern.FormatInfo
import com.kraftadmin.annotations.KraftAdminCustomAction
import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.annotations.KraftAdminLookup
import com.kraftadmin.config.JpaDataProviderFactory
import com.kraftadmin.config.SpringKraftAdminProperties
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.utils.logging.KraftAdminAuditor
import com.kraftadmin.persistence.jpa.provider.JpaDataProvider
import com.kraftadmin.security.SecurityProviderChain
import com.kraftadmin.spi.AbstractResource
import com.kraftadmin.spi.KraftAdminResource
import com.kraftadmin.spi.SelectOption
import com.kraftadmin.ui_descriptors.ColumnDescriptor
import com.kraftadmin.ui_descriptors.KraftActionDescriptor
import com.kraftadmin.ui_descriptors.LookupDescriptor
import com.kraftadmin.util.JakartaValidationExtractor
import com.kraftadmin.util.SpringBootTelemetryService
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.utils.telementary.KraftTelemetryService
import jakarta.persistence.*
import org.springframework.context.ApplicationContext
import org.springframework.transaction.support.TransactionTemplate
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

object ResourceGenerator {

    // Instantiate the extractor once
    private val validationExtractor = JakartaValidationExtractor()

    fun <T : Annotation> resolveAnnotation(
        javaField: java.lang.reflect.Field?,
        prop: KProperty<*>?,
        annotationClass: KClass<T>
    ): T? {
        // 1. Java backing field
        javaField?.getAnnotation(annotationClass.java)?.let { return it }
        // 2. Kotlin getter
        prop?.javaGetter?.getAnnotation(annotationClass.java)?.let { return it }
        // 3. Kotlin property metadata
        prop?.annotations?.filterIsInstance(annotationClass.java)?.firstOrNull()?.let { return it }
        // 4. Kotlin memberProperties on declaring class
        prop?.let { p ->
            runCatching {
                javaField?.declaringClass?.kotlin?.memberProperties
                    ?.find { it.name == p.name }
                    ?.annotations
                    ?.filterIsInstance(annotationClass.java)
                    ?.firstOrNull()
            }.getOrNull()?.let { return it }
        }
        // 5. Java setter fallback
        javaField?.declaringClass?.methods
            ?.firstOrNull { it.name == "set${javaField.name.replaceFirstChar { c -> c.uppercase() }}" }
            ?.getAnnotation(annotationClass.java)
            ?.let { return it }

        return null
    }

    private fun isRelationAnnotationPresent(
        javaField: java.lang.reflect.Field,
        prop: KProperty<*>,
        annotationClass: KClass<out Annotation>
    ): Boolean {
        return javaField.isAnnotationPresent(annotationClass.java)
                || prop.javaGetter?.isAnnotationPresent(annotationClass.java) == true
                || prop.annotations.any { it.annotationClass == annotationClass }
    }

    fun <T : Any> generate(
        entityClass: Class<T>,
        context: ApplicationContext,
        properties: SpringKraftAdminProperties
    ): KraftAdminResource<T> {
        val kClass = entityClass.kotlin
        println("\n\n========== GENERATING RESOURCE FOR: ${kClass.simpleName} ==========")

        val resource = object : AbstractResource<T>(
            name = kClass.simpleName ?: "Unknown",
            label = kClass.simpleName ?: "Unknown",
            entityClass = kClass
        ) {
            init {
                kClass.memberProperties.forEach { prop ->
                    println("\n--- PROP: ${kClass.simpleName}.${prop.name} ---")

                    // === STEP 1: Resolve javaField ===
                    val javaField = prop.javaField
                    println("  javaField: ${javaField?.name ?: "NULL"}")
                    if (javaField == null) {
                        println("  SKIP: javaField is null")
                        return@forEach
                    }

                    // === STEP 2: Dump ALL annotations ===
                    println("  [Java field annotations]:")
                    javaField.annotations.forEach { println("    - $it") }
                    println("  [Kotlin prop.annotations]:")
                    prop.annotations.forEach { println("    - $it") }
                    println("  [Kotlin getter annotations]:")
                    prop.javaGetter?.annotations?.forEach { println("    - $it") }

                    // === STEP 3: Skip transient/static ===
                    if (javaField.isAnnotationPresent(Transient::class.java) || Modifier.isStatic(javaField.modifiers)) {
                        println("  SKIP: Transient or Static")
                        return@forEach
                    }

                    // === STEP 4: Detect relation type ===
                    val isOneToOne  = isRelationAnnotationPresent(javaField, prop, OneToOne::class)
                    val isManyToOne = isRelationAnnotationPresent(javaField, prop, ManyToOne::class)
                    val isManyToMany = isRelationAnnotationPresent(javaField, prop, ManyToMany::class)
                    val isOneToMany = isRelationAnnotationPresent(javaField, prop, OneToMany::class)
                    println("  isOneToOne=$isOneToOne isManyToOne=$isManyToOne isManyToMany=$isManyToMany isOneToMany=$isOneToMany")

                    // === STEP 5: Resolve target entity class ===
                    val targetEntityClass: KClass<*>? = when {
                        isManyToOne || isOneToOne -> {
                            val resolved = prop.returnType.classifier as? KClass<*>
                            println("  targetEntityClass (xToOne via returnType.classifier): $resolved")
                            resolved
                        }
                        isManyToMany || isOneToMany -> {
                            val resolved = prop.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                                ?: run {
                                    val paramType = javaField.genericType as? java.lang.reflect.ParameterizedType
                                    (paramType?.actualTypeArguments?.firstOrNull() as? Class<*>)?.kotlin
                                }
                            println("  targetEntityClass (xToMany via generic): $resolved")
                            resolved
                        }
                        else -> {
                            println("  targetEntityClass: null (not a relation)")
                            null
                        }
                    }

                    // === STEP 6: Resolve type and default ===
                    val (colType, defaultVal) = resolveTypeAndDefault(prop, isOneToOne, isManyToOne, isManyToMany, isOneToMany)
                    println("  colType=$colType defaultVal=$defaultVal")

                    // === STEP 7: Resolve lookup config ===
                    val lookupConfig = targetEntityClass?.let { tkc ->
                        println("  [Lookup resolution for ${prop.name} -> ${tkc.simpleName}]")

                        val fieldAnn = resolveAnnotation(javaField, prop, KraftAdminLookup::class)
                        println("  fieldAnn (KraftAdminLookup): $fieldAnn")

                        val finalAnn = fieldAnn
                            ?: tkc.java.getAnnotation(KraftAdminLookup::class.java).also {
                                println("  targetClassAnn fallback: $it")
                            }

                        val searchField = when {
                            finalAnn != null && finalAnn.displayField.isNotBlank() -> {
                                println("  searchField from annotation: ${finalAnn.displayField}")
                                finalAnn.displayField
                            }
                            else -> {
                                val discovered = discoverDefaultSearchField(tkc)
                                println("  searchField from discovery: $discovered")
                                discovered
                            }
                        }

                        val lookupKey = if (finalAnn != null && finalAnn.lookupKey.isNotBlank())
                            finalAnn.lookupKey else "id"

                        println("  FINAL LookupDescriptor(targetEntity=${tkc.simpleName}, searchField=$searchField, lookupKey=$lookupKey)")
                        LookupDescriptor(
                            targetEntity = tkc.simpleName ?: "Unknown",
                            searchField = searchField,
                            lookupKey = lookupKey
                        )
                    }

                    // === STEP 8: Sub-columns for embedded ===
                    val subCols = if (colType == FormInputType.OBJECT &&
                        (javaField.isAnnotationPresent(Embedded::class.java) ||
                                prop.returnType.classifier?.let {
                                    (it as KClass<*>).java.isAnnotationPresent(Embeddable::class.java)
                                } == true)
                    ) {
                        generateSubColumns(prop.returnType.classifier as? KClass<*>)
                    } else null

                    // === STEP 9: Enum options ===
                    val options = if (colType == FormInputType.SELECT) {
                        (prop.returnType.classifier as? KClass<*>)?.java?.enumConstants?.map {
                            SelectOption(label = it.toString(), value = it.toString())
                        }
                    } else null

                    // === STEP 10: Build column ===
                    val rules = validationExtractor.extractRules(javaField)
                    val messages = validationExtractor.extractMessages(javaField)

                    // === STEP 11: Build column ===
                    column(
                        name = prop.name,
                        label = prop.name
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replaceFirstChar { it.uppercase() },
                        type = colType,
                        defaultValue = defaultVal,
                        subColumns = subCols,
                        selectOptions = options,
                        lookup = lookupConfig,
                        searchable = true,
                        sortable = true,
                        // Use the DSL as the source of truth for "required"
                        required = rules.contains("required"),

                        validationRules = rules.ifBlank { null },
                        validationMessages = if (messages.isEmpty()) null else messages,

                        placeholder = if (targetEntityClass != null) "Search ${prop.name}..." else "Enter ${prop.name}",
                        visible = !prop.name.equals("id", ignoreCase = true) && !isOneToMany
                    )
                }
            }

            private fun discoverDefaultSearchField(targetKClass: KClass<*>): String {
                val props = targetKClass.memberProperties
                val manual = props.find { p ->
                    p.javaField?.getAnnotation(KraftAdminField::class.java)?.displayField == true
                }
                if (manual != null) return manual.name
                val common = props.find {
                    it.name.lowercase() in listOf("title", "name", "label", "provider")
                }
                if (common != null) return common.name
                return props.find {
                    it.returnType.classifier == String::class && !it.name.contains("Id")
                }?.name ?: "id"
            }

            private fun generateSubColumns(kClass: KClass<*>?): List<ColumnDescriptor> {
                if (kClass == null) return emptyList()
                return kClass.memberProperties.mapNotNull { prop ->
                    val javaField = prop.javaField ?: return@mapNotNull null
                    if (javaField.isAnnotationPresent(Transient::class.java) ||
                        Modifier.isStatic(javaField.modifiers)) return@mapNotNull null

                    val (type, default) = resolveTypeAndDefault(prop, false, false, false, false)

                    // Extract rules for sub-fields
                    val subRules = validationExtractor.extractRules(javaField)
                    val subMessages = validationExtractor.extractMessages(javaField)

                    ColumnDescriptor(
                        name = prop.name,
                        label = prop.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .replaceFirstChar { it.uppercase() },
                        type = type.name,
                        defaultValue = default,
                        subColumns = if (type == FormInputType.OBJECT) generateSubColumns(prop.returnType.classifier as? KClass<*>) else null,
                        selectOptions = if (type == FormInputType.SELECT) (prop.returnType.classifier as? KClass<*>)?.java?.enumConstants?.map {
                            SelectOption(label = it.toString(), value = it.toString())
                        } else null,

                        // NEW: Sub-column validation
                        validationRules = subRules.ifBlank { null },
                        validationMessages = if (subMessages.isEmpty()) null else subMessages,

                        searchable = true,
                        sortable = true,
                        visible = true,
                        required = subRules.contains("required"),
                        placeholder = "Enter ${prop.name}"
                    )
                }
            }

            // Passing booleans avoids re-checking annotations inside resolveTypeAndDefault
            private fun resolveTypeAndDefault(
                prop: KProperty1<*, *>,
                isOneToOne: Boolean,
                isManyToOne: Boolean,
                isManyToMany: Boolean,
                isOneToMany: Boolean
            ): Pair<FormInputType, Any?> {
                val field = prop.javaField
                val classifier = prop.returnType.classifier as? KClass<*>

                return when {
                    field?.isAnnotationPresent(KraftAdminField::class.java) == true -> {
                        val annotation = field.getAnnotation(KraftAdminField::class.java)
                        annotation.inputType to null
                    }
                    // Relations → RELATION type
                    isOneToOne || isManyToOne -> {
                        FormInputType.RELATION to null
                    }
                    isManyToMany || isOneToMany -> {
                        FormInputType.MULTI_RELATION to emptyList<String>()
                    }
                    // Embedded objects
                    field?.isAnnotationPresent(Embedded::class.java) == true ||
                            classifier?.java?.isAnnotationPresent(Embeddable::class.java) == true -> {
                        FormInputType.OBJECT to createDefaultMapForClass(classifier)
                    }
                    field?.isAnnotationPresent(ElementCollection::class.java) == true -> {
                        FormInputType.ARRAY to emptyList<Any>()
                    }

                    List::class.java.isAssignableFrom(field?.type) &&
                            !field?.isAnnotationPresent(OneToMany::class.java)!! &&
                            !field.isAnnotationPresent(ManyToMany::class.java) -> {
                        FormInputType.ARRAY to emptyList<Any>()
                    }

                    classifier?.isSubclassOf(Enum::class) == true -> FormInputType.SELECT to null
                    classifier == String::class -> FormInputType.TEXT to ""
                    classifier == Boolean::class -> FormInputType.CHECKBOX to false
                    classifier?.isSubclassOf(Number::class) == true -> FormInputType.NUMBER to 0
                    classifier == java.time.LocalDate::class -> FormInputType.DATE to null
                    classifier == java.time.LocalDateTime::class -> FormInputType.DATETIME to null
                    else -> FormInputType.TEXT to null
                }
            }

            private fun createDefaultMapForClass(kClass: KClass<*>?): Map<String, Any?> {
                if (kClass == null) return emptyMap()
                val map = mutableMapOf<String, Any?>()
                try {
                    kClass.memberProperties.forEach { prop ->
                        val (_, defaultValue) = resolveTypeAndDefault(prop, false, false, false, false)
                        map[prop.name] = defaultValue
                    }
                } catch (e: Exception) { }
                return map
            }

            override val customActions: List<KraftActionDescriptor>
                get() {
                    return kClass.findAnnotations<KraftAdminCustomAction>().map { action ->
                        KraftActionDescriptor(
                            name = action.name,
                            label = action.label.ifEmpty { action.name.replace("-", " ").capitalize() },
                            icon = action.icon,
                            variant = action.variant
                        )
                    }
                }

            override fun getIdentifier(entity: T): Any {
                val idProp = kClass.memberProperties.find {
                    it.name.equals("id", ignoreCase = true) ||
                            it.javaField?.isAnnotationPresent(Id::class.java) == true
                } ?: throw IllegalStateException("No id property found for ${kClass.simpleName}")
                return idProp.getter.call(entity)
                    ?: throw IllegalStateException("Id is null")
            }
        }

        val factory = context.getBeanProvider(JpaDataProviderFactory::class.java).ifAvailable
        if (factory != null && entityClass.isAnnotationPresent(Entity::class.java)) {
            resource.dataProvider = JpaDataProvider(
                factory.entityManager,
                entityClass = kClass,
                transactionTemplate = context.getBean(TransactionTemplate::class.java),
                adminStorageProvider = context.getBean(AdminStorageProvider::class.java),
                kraftAdminAuditor = context.getBean(KraftAdminAuditor::class.java),
                securityChain = context.getBean(SecurityProviderChain::class.java),
                properties = properties,
                telemetryService = context.getBean(KraftTelemetryService::class.java)
            )
        }

        return resource
    }
}