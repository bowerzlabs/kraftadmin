package util

import com.kraftadmin.annotations.KraftAdminField
import annotations.KraftAnnotationUtils
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.ui_descriptors.ColumnDescriptor
import com.kraftadmin.utils.validation.KraftValidationExtractor
import org.slf4j.LoggerFactory
import kotlin.Annotation as JavaAnnotation
import java.lang.reflect.Field

/**
 * Extracts validation rules from Jakarta Validation annotations WITHOUT
 * a hard compile/runtime dependency on jakarta.validation-api.
 *
 * All Jakarta annotation lookups go through fully-qualified class NAME
 * strings + reflection, never direct `import jakarta.validation.constraints.*`
 * or `SomeAnnotation::class` references. This means:
 *   - The class loads fine even if the consumer never added
 *     spring-boot-starter-validation / jakarta.validation-api to their app.
 *   - If those annotations ARE present on the classpath, this extractor
 *     reads them dynamically via Class.forName + reflection.
 */
class JakartaValidationExtractor : KraftValidationExtractor {

    private val log = LoggerFactory.getLogger(JakartaValidationExtractor::class.java)

    // ✅ Fully-qualified Jakarta annotation class names — strings only, never imported
    private object JakartaAnnotations {
        const val NOT_NULL = "jakarta.validation.constraints.NotNull"
        const val NOT_BLANK = "jakarta.validation.constraints.NotBlank"
        const val NOT_EMPTY = "jakarta.validation.constraints.NotEmpty"
        const val SIZE = "jakarta.validation.constraints.Size"
        const val MIN = "jakarta.validation.constraints.Min"
        const val MAX = "jakarta.validation.constraints.Max"
        const val DECIMAL_MIN = "jakarta.validation.constraints.DecimalMin"
        const val DECIMAL_MAX = "jakarta.validation.constraints.DecimalMax"
        const val POSITIVE = "jakarta.validation.constraints.Positive"
        const val POSITIVE_OR_ZERO = "jakarta.validation.constraints.PositiveOrZero"
        const val NEGATIVE = "jakarta.validation.constraints.Negative"
        const val PAST = "jakarta.validation.constraints.Past"
        const val FUTURE = "jakarta.validation.constraints.Future"
        const val EMAIL = "jakarta.validation.constraints.Email"
        const val PATTERN = "jakarta.validation.constraints.Pattern"
    }

    private object SystemManagedAnnotations {
        const val JAKARTA_ID = "jakarta.persistence.Id"
        const val JAKARTA_TRANSIENT = "jakarta.persistence.Transient"
        const val SPRING_DATA_ID = "org.springframework.data.annotation.Id"
        const val CREATED_DATE = "org.springframework.data.annotation.CreatedDate"
        const val LAST_MODIFIED_DATE = "org.springframework.data.annotation.LastModifiedDate"
        const val CREATION_TIMESTAMP = "org.hibernate.annotations.CreationTimestamp"
        const val UPDATE_TIMESTAMP = "org.hibernate.annotations.UpdateTimestamp"
    }

    /**
     * Safely checks if an annotation with the given fully-qualified class
     * name is present on the field. Returns false (not throws) if the
     * annotation class isn't on the classpath at all.
     */
    private fun hasAnnotation(f: Field, fqcn: String): Boolean {
        return try {
            val annotationClass = Class.forName(fqcn) as Class<out JavaAnnotation>
            f.isAnnotationPresent(annotationClass)
        } catch (e: ClassNotFoundException) {
            false // annotation type isn't on the classpath — treat as absent
        } catch (e: Exception) {
            log.debug("Failed to check annotation $fqcn on field ${f.name}: ${e.message}")
            false
        }
    }

    /**
     * Safely retrieves an annotation instance by FQCN and reads a named
     * attribute off it via reflection. Returns null on any failure.
     */
    private fun <T> getAnnotationAttribute(f: Field, fqcn: String, attributeName: String): T? {
        return try {
            val annotationClass = Class.forName(fqcn) as Class<out JavaAnnotation>
            val annotation = f.getAnnotation(annotationClass) ?: return null
            @Suppress("UNCHECKED_CAST")
            annotationClass.getMethod(attributeName).invoke(annotation) as? T
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            log.debug("Failed to read $attributeName from $fqcn on field ${f.name}: ${e.message}")
            null
        }
    }

    override fun extractRules(field: Any): String {
        val f = field as? Field ?: return ""
        if (isSystemManaged(f)) return ""

        val rules = mutableListOf<String>()

        // --- 1. KraftAdminField (always available — our own annotation) ---
        val admin = KraftAnnotationUtils.getAnnotation(f, KraftAdminField::class)

        if (admin != null) {
            if (admin.required) rules.add("required")
            if (admin.regex.isNotEmpty()) rules.add("regex:${admin.regex}")

            when (admin.inputType) {
                FormInputType.TEXT, FormInputType.TEXTAREA, FormInputType.WYSIWYG -> rules.add("string")
                FormInputType.NUMBER, FormInputType.RANGE -> rules.add("numeric")
                FormInputType.COLOR -> rules.add("hexColor")
                FormInputType.EMAIL -> rules.add("email")
                FormInputType.TEL -> rules.add("tel")
                FormInputType.URL -> rules.add("url")
                FormInputType.PASSWORD -> {
                    rules.add("minLength:8")
                    rules.add("mustContainUppercase")
                    rules.add("mustContainSpecialChar")
                }
                FormInputType.DATE, FormInputType.DATETIME, FormInputType.TIME -> rules.add("date")
                else -> {}
            }
        }

        // --- 2. Requirement checks — string-based, safe if jakarta.validation absent ---
        val isRequired = listOf(
            JakartaAnnotations.NOT_NULL,
            JakartaAnnotations.NOT_BLANK,
            JakartaAnnotations.NOT_EMPTY
        ).any { hasAnnotation(f, it) }

        if (isRequired && !rules.contains("required")) {
            rules.add("required")
        }

        // --- 3. Format checks ---
        if (hasAnnotation(f, JakartaAnnotations.SIZE)) {
            getAnnotationAttribute<Int>(f, JakartaAnnotations.SIZE, "min")?.let { rules.add("minLen:$it") }
            getAnnotationAttribute<Int>(f, JakartaAnnotations.SIZE, "max")?.let { rules.add("maxLen:$it") }
        }

        getAnnotationAttribute<Long>(f, JakartaAnnotations.MIN, "value")?.let { rules.add("min:$it") }
        getAnnotationAttribute<Long>(f, JakartaAnnotations.MAX, "value")?.let { rules.add("max:$it") }
        getAnnotationAttribute<String>(f, JakartaAnnotations.DECIMAL_MIN, "value")?.let { rules.add("min:$it") }
        getAnnotationAttribute<String>(f, JakartaAnnotations.DECIMAL_MAX, "value")?.let { rules.add("max:$it") }

        // --- 4. Numerical signs ---
        if (hasAnnotation(f, JakartaAnnotations.POSITIVE)) rules.add("positive")
        if (hasAnnotation(f, JakartaAnnotations.POSITIVE_OR_ZERO)) rules.add("min:0")
        if (hasAnnotation(f, JakartaAnnotations.NEGATIVE)) rules.add("negative")

        // --- 5. Date & pattern & email ---
        if (hasAnnotation(f, JakartaAnnotations.PAST)) rules.add("past")
        if (hasAnnotation(f, JakartaAnnotations.FUTURE)) rules.add("future")
        if (hasAnnotation(f, JakartaAnnotations.EMAIL)) rules.add("email")

        getAnnotationAttribute<String>(f, JakartaAnnotations.PATTERN, "regexp")?.let {
            rules.add("regex:$it")
        }

        return rules.distinct().joinToString("|")
    }

    override fun extractMessages(field: Any): Map<String, String> {
        val f = field as? Field ?: return emptyMap()
        val messages = mutableMapOf<String, String>()

        val adminField = KraftAnnotationUtils.getAnnotation(f, KraftAdminField::class)

        // 1. Manual overrides — always available
        if (adminField != null && adminField.validationMessage.isNotEmpty()) {
            if (adminField.required) messages["required"] = adminField.validationMessage
            messages["regex"] = adminField.validationMessage
        }

        // 2. Jakarta overrides — string-based, safe if absent
        listOf(
            JakartaAnnotations.NOT_NULL,
            JakartaAnnotations.NOT_BLANK,
            JakartaAnnotations.NOT_EMPTY
        ).forEach { fqcn ->
            val msg = getAnnotationAttribute<String>(f, fqcn, "message")
            if (!msg.isNullOrEmpty() && !msg.startsWith("{jakarta")) {
                messages.putIfAbsent("required", msg)
            }
        }

        // 3. Fallback
        val isJakartaRequired = listOf(
            JakartaAnnotations.NOT_NULL,
            JakartaAnnotations.NOT_BLANK,
            JakartaAnnotations.NOT_EMPTY
        ).any { hasAnnotation(f, it) }

        if (messages["required"] == null && (adminField?.required == true || isJakartaRequired)) {
            messages["required"] = "${adminField?.label?.ifBlank { f.name } ?: f.name} is required"
        }

        // 4. Derived messages from FormInputType — always available
        if (adminField != null) {
            val label = adminField.label.ifBlank { f.name }
            when (adminField.inputType) {
                FormInputType.EMAIL -> messages.putIfAbsent("email", "Please enter a valid email address")
                FormInputType.TEL -> messages.putIfAbsent("tel", "Please enter a valid phone number")
                FormInputType.URL -> messages.putIfAbsent("url", "Please enter a valid URL (starting with http/https)")
                FormInputType.COLOR -> messages.putIfAbsent("hexColor", "Please select a valid hex color")
                FormInputType.PASSWORD -> {
                    messages.putIfAbsent("minLength", "$label must be at least 8 characters")
                    messages.putIfAbsent("mustContainUppercase", "$label must contain an uppercase letter")
                }
                else -> {}
            }
        }

        return messages
    }

    override fun validate(field: Any?, value: Any?): List<String> {
        val (required, rules, messages, label, type, subCols) = when (field) {
            is KraftAdminColumn -> {
                ReadOnlyMetadata(field.required, field.validationRules, field.validationMessages, field.label, field.type.name, field.subColumns)
            }
            is ColumnDescriptor -> {
                ReadOnlyMetadata(field.required, field.validationRules, field.validationMessages, field.label, field.type, field.subColumns)
            }
            else -> {
                log.error("Unknown field type: ${field?.javaClass?.name}")
                return emptyList()
            }
        }

        val errors = mutableListOf<String>()

        val isMissing = when (value) {
            null -> true
            is String -> value.trim().isEmpty()
            is Collection<*> -> value.isEmpty()
            is Map<*, *> -> value.isEmpty()
            else -> false
        }

        if (required && isMissing) {
            errors.add(messages?.get("required") ?: "$label is required")
            return errors
        }

        if (type == "OBJECT" && subCols != null && !isMissing) {
            val nestedMap = value as? Map<String, Any?> ?: emptyMap()
            subCols.forEach { subCol ->
                val subErrors = validate(subCol, nestedMap[subCol.name])
                errors.addAll(subErrors)
            }
        }

        if (!isMissing && rules != null) {
            val str = value.toString()
            rules.split("|").forEach { rule ->
                val valid = when (rule) {
                    "email" -> str.matches(Regex(".+@.+\\..+"))
                    "hexColor" -> str.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))
                    "tel" -> str.length >= 7
                    "numeric" -> str.toDoubleOrNull() != null
                    else -> true
                }
                if (!valid) errors.add(messages?.get(rule) ?: "Invalid $rule")
            }
        }

        return errors
    }

    data class ReadOnlyMetadata(
        val required: Boolean,
        val rules: String?,
        val messages: Map<String, String>?,
        val label: String,
        val type: String,
        val subCols: List<ColumnDescriptor>?
    )

    /**
     * String-based — safe even if jakarta.persistence / org.hibernate /
     * org.springframework.data aren't all present (e.g. a Mongo-only app
     * without JPA, or vice versa).
     */
    private fun isSystemManaged(f: Field): Boolean {
        return hasAnnotation(f, SystemManagedAnnotations.JAKARTA_ID) ||
                hasAnnotation(f, SystemManagedAnnotations.SPRING_DATA_ID) ||
                hasAnnotation(f, SystemManagedAnnotations.JAKARTA_TRANSIENT) ||
                hasAnnotation(f, SystemManagedAnnotations.CREATED_DATE) ||
                hasAnnotation(f, SystemManagedAnnotations.LAST_MODIFIED_DATE) ||
                hasAnnotation(f, SystemManagedAnnotations.CREATION_TIMESTAMP) ||
                hasAnnotation(f, SystemManagedAnnotations.UPDATE_TIMESTAMP) ||
                listOf("createdAt", "updatedAt", "id").contains(f.name)
    }
}