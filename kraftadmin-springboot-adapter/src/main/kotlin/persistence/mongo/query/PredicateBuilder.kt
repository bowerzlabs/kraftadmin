package persistence.mongo.query

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.query.KraftFilter
import org.springframework.data.mongodb.core.query.Criteria

/**
 * Builds Mongo Criteria from KraftFilter descriptors.
 * Mirrors persistence.jpa.query.PredicateBuilder, but operates directly on
 * the shared com.kraftadmin.query.KraftFilter type (the same one JPA's
 * PredicateBuilder converts its own Filter into for lifecycle events) —
 * so there's no separate Mongo-specific filter vocabulary to keep in sync.
 */
object PredicateBuilder {

    private val logger = KraftAdminLogging.logger(javaClass)

    fun build(filters: List<KraftFilter>): List<Criteria> {
        return filters.mapNotNull { filter ->
            try {
                buildCriteria(filter)
            } catch (e: Exception) {
                logger.warn("Could not build Mongo criteria for filter $filter: ${e.message}")
                null
            }
        }
    }

    private fun buildCriteria(filter: KraftFilter): Criteria? {
        return when (filter) {
            is KraftFilter.Equals ->
                Criteria.where(filter.field).`is`(filter.value)

            is KraftFilter.Like ->
                Criteria.where(filter.field).regex(".*${Regex.escape(filter.value)}.*", "i")

            is KraftFilter.GreaterThan ->
                Criteria.where(filter.field).gt(filter.value)

            is KraftFilter.LessThan ->
                Criteria.where(filter.field).lt(filter.value)

            is KraftFilter.Between ->
                Criteria.where(filter.field).gte(filter.from).lte(filter.to)

            is KraftFilter.In ->
                Criteria.where(filter.field).`in`(filter.values)

            is KraftFilter.IsNull ->
                Criteria.where(filter.field).isNull

            is KraftFilter.IsNotNull ->
                Criteria.where(filter.field).ne(null)

            is KraftFilter.Search ->
                searchCriteria(filter.fields, filter.value)
        }
    }

    /**
     * OR'd regex match across the given fields — mirrors JPA's Search filter
     * and FetchAll's existing global-search regex approach.
     */
    fun searchCriteria(fields: List<String>, searchQuery: String): Criteria? {
        if (searchQuery.isBlank() || fields.isEmpty()) return null

        val regex = ".*${Regex.escape(searchQuery.trim())}.*"
        val criteria = fields.map { Criteria.where(it).regex(regex, "i") }

        return if (criteria.size == 1) criteria.first()
        else Criteria().orOperator(*criteria.toTypedArray())
    }
}