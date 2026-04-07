package annotations

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaGetter

object KraftAnnotationUtils {

    // Cache the result. We use 'Any' because we might store the annotation OR a sentinel object.
    private val annotationCache = ConcurrentHashMap<String, Any>()
    private val NULL_SENTINEL = Any()

    fun <T : Annotation> getAnnotation(field: Field, annotationClass: KClass<T>): T? {
        val cacheKey = "${field.declaringClass.name}.${field.name}.${annotationClass.java.name}"

        val cached = annotationCache.getOrPut(cacheKey) {
            resolve(field, annotationClass) ?: NULL_SENTINEL
        }

        return if (cached === NULL_SENTINEL) null else cached as T?
    }

    private fun <T : Annotation> resolve(field: Field, annotationClass: KClass<T>): T? {
        val target = annotationClass.java

        // 1. Direct Field
        field.getAnnotation(target)?.let { return it }

        // 2. Kotlin Property Context
        try {
            val kClass = field.declaringClass.kotlin
            val kProp = kClass.memberProperties.find { it.name == field.name }

            kProp?.annotations?.filterIsInstance(target)?.firstOrNull()?.let { return it }
            kProp?.javaGetter?.getAnnotation(target)?.let { return it }

            kClass.primaryConstructor?.parameters
                ?.find { it.name == field.name }
                ?.annotations
                ?.filterIsInstance(target)
                ?.firstOrNull()
                ?.let { return it }
        } catch (e: Throwable) {
            // Not a Kotlin class or reflect missing
        }

        // 3. Setter Fallback
        val setterName = "set${field.name.replaceFirstChar { it.uppercase() }}"
        field.declaringClass.methods
            .find { it.name == setterName }
            ?.getAnnotation(target)
            ?.let { return it }

        return null
    }
}