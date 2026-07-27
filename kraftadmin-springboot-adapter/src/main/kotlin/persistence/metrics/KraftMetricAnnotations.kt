package persistence.metrics

import com.kraftadmin.annotations.KraftAdminMetric
import com.kraftadmin.annotations.KraftAdminMetrics

object KraftMetricAnnotations {

    fun findOn(entityClass: Class<*>): List<KraftAdminMetric> {
        val result = mutableListOf<KraftAdminMetric>()

        // Single/direct annotation
        entityClass
            .getDeclaredAnnotation(KraftAdminMetric::class.java)
            ?.let(result::add)

        // Java/Kotlin repeatable container
        entityClass
            .getDeclaredAnnotation(KraftAdminMetrics::class.java)
            ?.value
            ?.let(result::addAll)

        return result.distinctBy { it.name }
    }
}