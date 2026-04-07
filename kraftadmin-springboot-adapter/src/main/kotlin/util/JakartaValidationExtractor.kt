//package com.kraftadmin.util
//
//import com.kraftadmin.annotations.KraftAdminField
//import com.kraftadmin.core.util.KraftAnnotationUtils
//import com.kraftadmin.enums.FormInputType
//import com.kraftadmin.spi.KraftAdminColumn
//import com.kraftadmin.ui_descriptors.ColumnDescriptor
//import com.kraftadmin.utils.validation.KraftValidationExtractor
//import jakarta.validation.constraints.DecimalMax
//import jakarta.validation.constraints.DecimalMin
//import jakarta.validation.constraints.Email
//import jakarta.validation.constraints.Future
//import jakarta.validation.constraints.Max
//import jakarta.validation.constraints.Min
//import jakarta.validation.constraints.Negative
//import jakarta.validation.constraints.NotBlank
//import jakarta.validation.constraints.NotEmpty
//import jakarta.validation.constraints.NotNull
//import jakarta.validation.constraints.Past
//import jakarta.validation.constraints.Pattern
//import jakarta.validation.constraints.Positive
//import jakarta.validation.constraints.PositiveOrZero
//import jakarta.validation.constraints.Size
//import org.hibernate.annotations.CreationTimestamp
//import org.hibernate.annotations.UpdateTimestamp
//import kotlin.reflect.KClass
//import kotlin.reflect.KProperty
//import kotlin.reflect.KProperty1
//import org.slf4j.LoggerFactory
//import org.springframework.core.annotation.AnnotationUtils.findAnnotation
//import kotlin.reflect.full.memberProperties
//import kotlin.reflect.full.primaryConstructor
//import kotlin.reflect.jvm.javaGetter
//
//class JakartaValidationExtractor : KraftValidationExtractor {
//
//    private val log = LoggerFactory.getLogger(JakartaValidationExtractor::class.java)
//
//    override fun extractRules(field: Any): String {
//        val f = field as? java.lang.reflect.Field ?: return ""
//        if (isSystemManaged(f)) return ""
//
//        // 💡 The Core Change: Resolve the Kotlin Property once
//        val kProp = f.declaringClass.kotlin.memberProperties.find { it.name == f.name }
//        val rules = mutableListOf<String>()
//
//        // --- 1. Resolve KraftAdminField (Our primary UI descriptor) ---
////        val admin1 = resolveAnnotation(f, kProp, KraftAdminField::class)
//        val admin = KraftAnnotationUtils.getAnnotation(f, KraftAdminField::class)
//
//        if (admin != null) {
//            if (admin.required) rules.add("required")
//            if (admin.regex.isNotEmpty()) rules.add("regex:${admin.regex}")
//
//            // Map UI types to rules
//            when (admin.inputType) {
//                FormInputType.TEXT, FormInputType.TEXTAREA, FormInputType.WYSIWYG -> rules.add("string")
//                FormInputType.NUMBER, FormInputType.RANGE -> rules.add("numeric")
//                FormInputType.COLOR -> rules.add("hexColor")
//                FormInputType.EMAIL -> rules.add("email")
//                FormInputType.TEL -> rules.add("tel")
//                FormInputType.URL -> rules.add("url")
//                FormInputType.PASSWORD -> {
//                    rules.add("minLength:8")
//                    rules.add("mustContainUppercase")
//                    rules.add("mustContainSpecialChar")
//                }
//                FormInputType.DATE, FormInputType.DATETIME, FormInputType.TIME -> rules.add("date")
//                else -> {}
//            }
//        }
//
//        // Requirement checks
//        val isRequired = listOf(NotNull::class, NotBlank::class, NotEmpty::class)
//            .any { resolveAnnotation(f, kProp, it) != null }
//
//        if (isRequired && !rules.contains("required")) {
//            rules.add("required")
//        }
//
//        // Format checks
//        resolveAnnotation(f, kProp, Size::class)?.let {
//            rules.add("minLen:${it.min}")
//            rules.add("maxLen:${it.max}")
//        }
//
//        resolveAnnotation(f, kProp, Min::class)?.let { rules.add("min:${it.value}") }
//        resolveAnnotation(f, kProp, Max::class)?.let { rules.add("max:${it.value}") }
//
//        f.getAnnotation(Min::class.java)?.let { rules.add("min:${it.value}") }
//        f.getAnnotation(Max::class.java)?.let { rules.add("max:${it.value}") }
//        f.getAnnotation(DecimalMin::class.java)?.let { rules.add("min:${it.value}") }
//        f.getAnnotation(DecimalMax::class.java)?.let { rules.add("max:${it.value}") }
//
//        // 4. Numerical Signs
//        if (f.isAnnotationPresent(Positive::class.java)) rules.add("positive")
//        if (f.isAnnotationPresent(PositiveOrZero::class.java)) rules.add("min:0")
//        if (f.isAnnotationPresent(Negative::class.java)) rules.add("negative")
//
//        // 5. Date & Pattern
//        if (f.isAnnotationPresent(Past::class.java)) rules.add("past")
//        if (f.isAnnotationPresent(Future::class.java)) rules.add("future")
//        if (f.isAnnotationPresent(Email::class.java)) rules.add("email")
//
//        f.getAnnotation(Pattern::class.java)?.let {
//            rules.add("regex:${it.regexp}")
//        }
//
//        if (resolveAnnotation(f, kProp, Email::class) != null) rules.add("email")
//
//        resolveAnnotation(f, kProp, Pattern::class)?.let {
//            rules.add("regex:${it.regexp}")
//        }
//
//        return rules.distinct().joinToString("|")
//    }
//
//    private fun isRequired(f: java.lang.reflect.Field, kProp: KProperty<*>?): Boolean {
//        return listOf(NotNull::class, NotBlank::class, NotEmpty::class)
//            .any { resolveAnnotation(f, kProp, it) != null }
//    }
//
//    override fun extractMessages(field: Any): Map<String, String> {
//        val f = field as? java.lang.reflect.Field ?: return emptyMap()
//        val kProp = f.declaringClass.kotlin.memberProperties.find { it.name == f.name }
//        val messages = mutableMapOf<String, String>()
//
//        val adminField = resolveAnnotation(f, kProp, KraftAdminField::class)
//
//        // 1. Manual Overrides
//        if (adminField != null && adminField.validationMessage.isNotEmpty()) {
//            if (adminField.required) messages["required"] = adminField.validationMessage
//            messages["regex"] = adminField.validationMessage
//        }
//
//        // 2. Jakarta Overrides (Detecting custom messages)
//        listOf(NotNull::class, NotBlank::class, NotEmpty::class).forEach { clazz ->
//            resolveAnnotation(f, kProp, clazz)?.let { ann ->
//                // Check if message is non-default (this logic depends on your specific Jakarta version)
//                val msg = when(ann) {
//                    is NotNull -> ann.message
//                    is NotBlank -> ann.message
//                    is NotEmpty -> ann.message
//                    else -> ""
//                }
//                if (msg.isNotEmpty() && !msg.startsWith("{jakarta")) {
//                    messages.putIfAbsent("required", msg)
//                }
//            }
//        }
//
//        // 3. Fallback
//        if (messages["required"] == null && (adminField?.required == true || isRequired(f, kProp))) {
//            messages["required"] = "${adminField?.label?.ifBlank { f.name } ?: f.name} is required"
//        }
//
//        //  Final Fallback: Derived messages from FormInputType
//        // This ensures that even if validationMessages was null in  JSON, it now has content.
//        if (adminField != null) {
//            val label = adminField.label.ifBlank { f.name }
//            when (adminField.inputType) {
//                FormInputType.EMAIL -> messages.putIfAbsent("email", "Please enter a valid email address")
//                FormInputType.TEL -> messages.putIfAbsent("tel", "Please enter a valid phone number")
//                FormInputType.URL -> messages.putIfAbsent("url", "Please enter a valid URL (starting with http/https)")
//                FormInputType.COLOR -> messages.putIfAbsent("hexColor", "Please select a valid hex color")
//                FormInputType.PASSWORD -> {
//                    messages.putIfAbsent("minLength", "$label must be at least 8 characters")
//                    messages.putIfAbsent("mustContainUppercase", "$label must contain an uppercase letter")
//                }
//                else -> {}
//            }
//        }
//
//        return messages
//    }
//
//    override fun validate(field: Any?, value: Any?): List<String> {
//        // 💡 Extract properties manually or via a helper since types differ
//        val (required, rules, messages, label, type, subCols) = when (field) {
//            is KraftAdminColumn -> {
//                ReadOnlyMetadata(field.required, field.validationRules, field.validationMessages, field.label, field.type.name, field.subColumns)
//            }
//            is ColumnDescriptor -> {
//                ReadOnlyMetadata(field.required, field.validationRules, field.validationMessages, field.label, field.type, field.subColumns)
//            }
//            else -> {
//                log.error("Unknown field type: ${field?.javaClass?.name}")
//                return emptyList()
//            }
//        }
//
//        val errors = mutableListOf<String>()
//
//        val isMissing = when (value) {
//            null -> true
//            is String -> value.trim().isEmpty()
//            is Collection<*> -> value.isEmpty()
//            is Map<*, *> -> value.isEmpty()
//            else -> false
//        }
//
//        // 1. Requirement Check
//        if (required && isMissing) {
//            errors.add(messages?.get("required") ?: "$label is required")
//            return errors
//        }
//
//        // 2. Recursive Check for OBJECT (Location)
//        if (type == "OBJECT" && subCols != null && !isMissing) {
//            val nestedMap = value as? Map<String, Any?> ?: emptyMap()
//            subCols.forEach { subCol ->
//                val subErrors = validate(subCol, nestedMap[subCol.name])
//                errors.addAll(subErrors)
//            }
//        }
//
//
//        // 3. Format Rules
//        if (!isMissing && rules != null) {
//            val str = value.toString()
//            rules.split("|").forEach { rule ->
//                val valid = when (rule) {
//                    "email" -> str.matches(Regex(".+@.+\\..+"))
//                    "hexColor" -> str.matches(Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$"))
//                    "tel" -> str.length >= 7
//                    "numeric" -> str.toDoubleOrNull() != null
//                    else -> true
//                }
//                if (!valid) errors.add(messages?.get(rule) ?: "Invalid $rule")
//            }
//        }
//
//        return errors
//    }
//
//    // Simple helper data class to normalize the two types
//    data class ReadOnlyMetadata(
//        val required: Boolean,
//        val rules: String?,
//        val messages: Map<String, String>?,
//        val label: String,
//        val type: String,
//        val subCols: List<ColumnDescriptor>?
//    )
//
//    private fun isSystemManaged(f: java.lang.reflect.Field): Boolean {
//        return f.isAnnotationPresent(jakarta.persistence.Id::class.java) ||
//                f.isAnnotationPresent(org.springframework.data.annotation.Id::class.java) ||
//                f.isAnnotationPresent(jakarta.persistence.Transient::class.java) ||
//                f.isAnnotationPresent(org.springframework.data.annotation.CreatedDate::class.java) ||
//                f.isAnnotationPresent(org.springframework.data.annotation.LastModifiedDate::class.java) ||
//                f.isAnnotationPresent(CreationTimestamp::class.java) ||
//                f.isAnnotationPresent(UpdateTimestamp::class.java) ||
//                listOf("createdAt", "updatedAt", "id").contains(f.name)
//    }
//
//}

package com.kraftadmin.util

import com.kraftadmin.annotations.KraftAdminField
import annotations.KraftAnnotationUtils
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.spi.KraftAdminColumn
import com.kraftadmin.ui_descriptors.ColumnDescriptor
import com.kraftadmin.utils.validation.KraftValidationExtractor
import jakarta.validation.constraints.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.slf4j.LoggerFactory

class JakartaValidationExtractor : KraftValidationExtractor {

    private val log = LoggerFactory.getLogger(JakartaValidationExtractor::class.java)

    override fun extractRules(field: Any): String {
        val f = field as? java.lang.reflect.Field ?: return ""
        if (isSystemManaged(f)) return ""

        val rules = mutableListOf<String>()

        // --- 1. Resolve KraftAdminField (Our primary UI descriptor) ---
        val admin = KraftAnnotationUtils.getAnnotation(f, KraftAdminField::class)

        if (admin != null) {
            if (admin.required) rules.add("required")
            if (admin.regex.isNotEmpty()) rules.add("regex:${admin.regex}")

            // Map UI types to rules
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

        // 2. Requirement checks
        val isRequired = listOf(NotNull::class, NotBlank::class, NotEmpty::class)
            .any { KraftAnnotationUtils.getAnnotation(f, it) != null }

        if (isRequired && !rules.contains("required")) {
            rules.add("required")
        }

        // 3. Format checks (Using Util for all Jakarta Constraints)
        KraftAnnotationUtils.getAnnotation(f, Size::class)?.let {
            rules.add("minLen:${it.min}")
            rules.add("maxLen:${it.max}")
        }

        KraftAnnotationUtils.getAnnotation(f, Min::class)?.let { rules.add("min:${it.value}") }
        KraftAnnotationUtils.getAnnotation(f, Max::class)?.let { rules.add("max:${it.value}") }
        KraftAnnotationUtils.getAnnotation(f, DecimalMin::class)?.let { rules.add("min:${it.value}") }
        KraftAnnotationUtils.getAnnotation(f, DecimalMax::class)?.let { rules.add("max:${it.value}") }

        // 4. Numerical Signs
        if (KraftAnnotationUtils.getAnnotation(f, Positive::class) != null) rules.add("positive")
        if (KraftAnnotationUtils.getAnnotation(f, PositiveOrZero::class) != null) rules.add("min:0")
        if (KraftAnnotationUtils.getAnnotation(f, Negative::class) != null) rules.add("negative")

        // 5. Date & Pattern & Email
        if (KraftAnnotationUtils.getAnnotation(f, Past::class) != null) rules.add("past")
        if (KraftAnnotationUtils.getAnnotation(f, Future::class) != null) rules.add("future")
        if (KraftAnnotationUtils.getAnnotation(f, Email::class) != null) rules.add("email")

        KraftAnnotationUtils.getAnnotation(f, Pattern::class)?.let {
            rules.add("regex:${it.regexp}")
        }

        return rules.distinct().joinToString("|")
    }

    override fun extractMessages(field: Any): Map<String, String> {
        val f = field as? java.lang.reflect.Field ?: return emptyMap()
        val messages = mutableMapOf<String, String>()

        val adminField = KraftAnnotationUtils.getAnnotation(f, KraftAdminField::class)

        // 1. Manual Overrides
        if (adminField != null && adminField.validationMessage.isNotEmpty()) {
            if (adminField.required) messages["required"] = adminField.validationMessage
            messages["regex"] = adminField.validationMessage
        }

        // 2. Jakarta Overrides
        listOf(NotNull::class, NotBlank::class, NotEmpty::class).forEach { clazz ->
            KraftAnnotationUtils.getAnnotation(f, clazz)?.let { ann ->
                val msg = when(ann) {
                    is NotNull -> ann.message
                    is NotBlank -> ann.message
                    is NotEmpty -> ann.message
                    else -> ""
                }
                if (msg.isNotEmpty() && !msg.startsWith("{jakarta")) {
                    messages.putIfAbsent("required", msg)
                }
            }
        }

        // 3. Fallback
        val isJakartaRequired = listOf(NotNull::class, NotBlank::class, NotEmpty::class)
            .any { KraftAnnotationUtils.getAnnotation(f, it) != null }

        if (messages["required"] == null && (adminField?.required == true || isJakartaRequired)) {
            messages["required"] = "${adminField?.label?.ifBlank { f.name } ?: f.name} is required"
        }

        // Final Fallback: Derived messages from FormInputType
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

    private fun isSystemManaged(f: java.lang.reflect.Field): Boolean {
        // Using direct isAnnotationPresent for system internal annotations as they are usually on fields
        return f.isAnnotationPresent(jakarta.persistence.Id::class.java) ||
                f.isAnnotationPresent(org.springframework.data.annotation.Id::class.java) ||
                f.isAnnotationPresent(jakarta.persistence.Transient::class.java) ||
                f.isAnnotationPresent(org.springframework.data.annotation.CreatedDate::class.java) ||
                f.isAnnotationPresent(org.springframework.data.annotation.LastModifiedDate::class.java) ||
                f.isAnnotationPresent(CreationTimestamp::class.java) ||
                f.isAnnotationPresent(UpdateTimestamp::class.java) ||
                listOf("createdAt", "updatedAt", "id").contains(f.name)
    }
}