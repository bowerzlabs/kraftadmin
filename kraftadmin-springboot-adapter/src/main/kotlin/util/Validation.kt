package com.kraftadmin.util

import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.enums.FormInputType
import com.kraftadmin.enums.FormInputType.*
import com.kraftadmin.utils.validation.KraftValidate
import jakarta.persistence.Column
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.validation.constraints.*
import java.lang.reflect.Field
import kotlin.reflect.KClass

open class Validation : KraftValidate {

    /**
     * Scans a JPA Entity field for Jakarta Validation and JPA annotations
     * to produce a DSL-like string for the Svelte frontend.
     */
    fun extractValidationRules1(field: Field): String {
        val rules = StringBuilder()

        // 1. Required Check
        if (isRequired(field)) {
            rules.append("required|")
        }

        // 2. Length & Size
        field.getAnnotation(Size::class.java)?.let {
            rules.append("size(min:${it.min},max:${it.max})|")
        }

        // 3. Numeric Constraints
        field.getAnnotation(Min::class.java)?.let { rules.append("min:${it.value}|") }
        field.getAnnotation(Max::class.java)?.let { rules.append("max:${it.value}|") }
        field.getAnnotation(DecimalMin::class.java)?.let { rules.append("min:${it.value}|") }
        field.getAnnotation(DecimalMax::class.java)?.let { rules.append("max:${it.value}|") }

        if (field.isAnnotationPresent(Positive::class.java)) rules.append("positive|")
        if (field.isAnnotationPresent(PositiveOrZero::class.java)) rules.append("min:0|")
        if (field.isAnnotationPresent(Negative::class.java)) rules.append("negative|")

        // 4. String Formats
        if (field.isAnnotationPresent(Email::class.java)) rules.append("email|")

        field.getAnnotation(Pattern::class.java)?.let {
            // Clean regex for JS consumption (removing extra escaping if necessary)
            rules.append("regex:${it.regexp}|")
        }

        // 5. Date Constraints
        if (field.isAnnotationPresent(Past::class.java)) rules.append("past|")
        if (field.isAnnotationPresent(Future::class.java)) rules.append("future|")

        // 6. KraftAdmin Specific Input Logic
                // Form input type-based validation
        if (field.javaClass.isAnnotationPresent(KraftAdminField::class.java)) {
            val kraftAdminField = field.javaClass.getAnnotation<KraftAdminField>(KraftAdminField::class.java)
            val formInputType: FormInputType = kraftAdminField.inputType
            when (formInputType) {
                TEXT, TEXTAREA, WYSIWYG -> rules.append("string|")
                NUMBER, RANGE -> rules.append("numeric|")
                COLOR -> rules.append("hexColor|")
                CHECKBOX, RADIO -> rules.append("boolean|")
                EMAIL -> rules.append("email|")
                PASSWORD -> rules.append("minLength:8|mustContainUppercase|mustContainSpecialChar|")
                FILE, IMAGE -> rules.append("file|")
                DATE, DATETIME, TIME -> rules.append("date|")
                TEL -> rules.append("tel|")
                URL -> rules.append("url|")
                SELECT -> TODO()
                MULTI_RELATION -> TODO()
                ARRAY -> TODO()
                VIDEO -> TODO()
                JSON -> TODO()
                UNSET -> TODO()
                OBJECT -> TODO()
                RELATION -> TODO()
                else -> {}
            }
        }

        return rules.toString().removeSuffix("|")
    }


    fun extractValidationRules(field: Field): String {
        val rules = mutableListOf<String>()

        // 1. Unified Required Check
        if (isRequired(field)) rules.add("required")

        // 2. Size/Length
        field.getAnnotation(Size::class.java)?.let {
            rules.add("minLen:${it.min}")
            if (it.max < Int.MAX_VALUE) rules.add("maxLen:${it.max}")
        }

        // 3. Numeric (Using standard names for the frontend)
        field.getAnnotation(Min::class.java)?.let { rules.add("min:${it.value}") }
        field.getAnnotation(Max::class.java)?.let { rules.add("max:${it.value}") }

        if (field.isAnnotationPresent(Email::class.java)) rules.add("email")

        // 4. KraftAdmin Annotation override
        field.getAnnotation(KraftAdminField::class.java)?.let { admin ->
            // Force the input type into the rules so the UI knows how to restrict typing
            when (admin.inputType) {
                FormInputType.NUMBER -> rules.add("numeric")
                FormInputType.URL -> rules.add("url")
                FormInputType.TEL -> rules.add("tel")
                else -> {}
            }
        }

        return rules.joinToString("|")
    }

    /**
     * Determines if a field must be provided based on JPA and Jakarta annotations.
     */
    private fun isRequired(field: Field): Boolean {
        return field.isAnnotationPresent(NotNull::class.java) ||
                field.isAnnotationPresent(NotBlank::class.java) ||
                field.isAnnotationPresent(NotEmpty::class.java) ||
                (field.getAnnotation(Column::class.java)?.nullable == false) ||
                (field.getAnnotation(ManyToOne::class.java)?.optional == false) ||
                (field.getAnnotation(OneToOne::class.java)?.optional == false)
    }


    fun validateValues(
        validationRules: MutableMap<String?, String?>,
        formValues: MutableMap<String?, String?>,
        fieldLabels: MutableMap<String?, String?>
    ): MutableMap<String?, String?> {
        val validationErrors: MutableMap<String?, String?> = HashMap<String?, String?>()
        for (entry in validationRules.entries) {
            val fieldName = entry.key
            val rules: String = entry.value!!
            val fieldValue = formValues.getOrDefault(fieldName, "").toString()

            // Required validation
            if (rules.contains("required") && fieldValue.isEmpty()) {
                validationErrors[fieldName] = fieldLabels[fieldName] + " is required."
            }

            // Size validation
            if (rules.contains("size")) {
                val min = extractSizeValue(rules, "min")
                val max = extractSizeValue(rules, "max")
                //                log.info("Extracted size for {}: min={}, max={}", fieldName, min, max);
                if (fieldValue.length !in min..max) {
                    validationErrors[fieldName] = fieldLabels[fieldName] + " must be between " + min + " and " + max + " characters."
                }
            }

            // Regex validation
            if (rules.contains("regex")) {
                val regex = extractRegex(rules)
//                log.info("Extracted regex for {}: {}", fieldName, regex)
                if (!fieldValue.matches(regex.toRegex())) {
                    validationErrors[fieldName] = fieldLabels[fieldName] + " format is invalid."
                }
            }
        }
        return validationErrors
    }

    // Helper methods to extract size constraints
    private fun extractSizeValue(rules: String, type: String): Int {
        val pattern = java.util.regex.Pattern.compile("size\\([^)]*" + type + ":(\\d+)[^)]*\\)")
        val matcher = pattern.matcher(rules)
        if (matcher.find()) {
            return matcher.group(1).toInt()
        }
        return if (type == "min") 5 else Int.Companion.MAX_VALUE // Defaults
    }

    private fun extractRegex(rules: String): String {
        val pattern = java.util.regex.Pattern.compile("regex: ([^|]+)")
        val matcher = pattern.matcher(rules)

        return if (matcher.find()) matcher.group(1).trim { it <= ' ' } else ""
    }

    override fun extractValidationSchema(clazz: KClass<*>): Map<String, String> {
        val schema = mutableMapOf<String, String>()

        // Use java.lang.reflect to get all fields including private ones
        clazz.java.declaredFields.forEach { field ->
            val rules = processFieldAnnotations(field)
            if (rules.isNotEmpty()) {
                schema[field.name] = rules
            }
        }
        return schema
    }

    override fun validate(
        rules: Map<String, String>,
        payload: Map<String, Any?>,
        labels: Map<String, String>
    ): Map<String, String> {
        TODO("Not yet implemented")
    }

    private fun processFieldAnnotations(field: Field): String {
        val rules = mutableListOf<String>()

        // Re-using your logic but cleaner
        if (isRequired(field)) rules.add("required")

        field.getAnnotation(Size::class.java)?.let {
            rules.add("size(min:${it.min},max:${it.max})")
        }

        field.getAnnotation(Email::class.java)?.let {
            rules.add("email")
        }

        return rules.joinToString("|")
    }

}

