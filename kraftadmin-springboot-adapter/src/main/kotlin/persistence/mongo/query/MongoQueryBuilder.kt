package persistence.mongo.query

import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.query.KraftFilter
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import kotlin.reflect.KClass

class MongoQueryBuilder<T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val entityClass: KClass<T>
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    private var filters: List<KraftFilter> = emptyList()
    private var searchQuery: String? = null
    private var searchFields: List<String> = emptyList()
    private var sortSpec: SortBuilder.SortSpec? = null
    private var allowedSortFields: List<String> = emptyList()
    private var pageSpec: PageableBuilder.PageSpec? = null

    fun where(filters: List<KraftFilter>): MongoQueryBuilder<T> {
        this.filters = filters
        return this
    }

    fun search(query: String?, fields: List<String>): MongoQueryBuilder<T> {
        logger.info("Search for ${entityClass.simpleName} with fields $fields and query $query")
        this.searchQuery = query
        this.searchFields = fields
        return this
    }

    fun sort(spec: SortBuilder.SortSpec?, allowedFields: List<String>): MongoQueryBuilder<T> {
        this.sortSpec = spec
        this.allowedSortFields = allowedFields
        return this
    }

    fun page(spec: PageableBuilder.PageSpec?): MongoQueryBuilder<T> {
        this.pageSpec = spec
        return this
    }

    /**
     * Count ignores sort entirely — total matching records is unaffected
     * by ordering, so this stays on the cheap plain-Query count path.
     */
    fun count(): Long {
        return try {
            mongoTemplate.count(buildMatchQuery(), entityClass.java)
        } catch (e: Exception) {
            logger.error("Count query failed for ${entityClass.simpleName}: ${e.message}", e)
            0L
        }
    }

    /**
     * Executes the query. If a valid sort is active, runs an aggregation
     * pipeline (match → addFields nulls-last flag → sort → skip → limit)
     * so blank/null values on the sort field always land last. If there's
     * no sort, falls back to a plain Query — cheaper, and sort ordering
     * doesn't matter when there's nothing to order by.
     */
    fun buildAndExecute(): List<T> {
        val hasValidSort = sortSpec != null && sortSpec!!.field in allowedSortFields

        return if (hasValidSort) {
            buildAndExecuteAggregation()
        } else {
            buildAndExecutePlainQuery()
        }
    }

    private fun buildAndExecutePlainQuery(): List<T> {
        return try {
            val query = buildMatchQuery()
            pageSpec?.let { PageableBuilder.apply(query, it) }
            mongoTemplate.find(query, entityClass.java)
        } catch (e: Exception) {
            logger.error("Query failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun buildAndExecuteAggregation(): List<T> {
        return try {
            val stages = mutableListOf<org.springframework.data.mongodb.core.aggregation.AggregationOperation>()

            val matchCriteria = buildMatchCriteria()
            if (matchCriteria != null) {
                stages.add(match(matchCriteria))
            }

            SortBuilder.addFieldsStage(sortSpec, allowedSortFields)?.let { stages.add(it) }
            SortBuilder.sortStage(sortSpec, allowedSortFields)?.let { stages.add(it) }

            pageSpec?.let { spec ->
                stages.add(skip(spec.offset))
                stages.add(limit(spec.effectiveSize.toLong()))
            }

            val aggregation = newAggregation(entityClass.java, stages)
            mongoTemplate.aggregate(aggregation, entityClass.java, entityClass.java).mappedResults
        } catch (e: Exception) {
            logger.error("Aggregation query failed for ${entityClass.simpleName}: ${e.message}", e)
            emptyList()
        }
    }


    private fun buildMatchCriteria(): Criteria? {
        val predicates = mutableListOf<Criteria>()

        if (filters.isNotEmpty()) {
            val filterCriteria = PredicateBuilder.build(filters)
            logger.info("Applying ${filterCriteria.size} explicit filters")
            predicates.addAll(filterCriteria)
        }

        if (!searchQuery.isNullOrBlank() && searchFields.isNotEmpty()) {
            val searchCriteria = PredicateBuilder.searchCriteria(searchFields, searchQuery!!)
            if (searchCriteria != null) {
                logger.info("Adding search criteria for query: $searchQuery")
                predicates.add(searchCriteria)
            } else {
                logger.warn("Search criteria was null for query: $searchQuery")
            }
        }

        return when {
            predicates.isEmpty() -> null
            predicates.size == 1 -> predicates.first()
            else -> Criteria().andOperator(*predicates.toTypedArray())
        }
    }

    private fun buildMatchQuery(): Query {
        val criteria = buildMatchCriteria() ?: return Query()
        return Query(criteria)
    }
}