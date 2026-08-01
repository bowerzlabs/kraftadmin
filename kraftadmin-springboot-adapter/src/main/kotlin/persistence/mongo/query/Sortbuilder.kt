package persistence.mongo.query

import org.bson.Document
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.AggregationOperationContext

/**
 * Mirrors persistence.jpa.query.SortBuilder's nulls-last behavior, but via
 * an aggregation pipeline instead of Criteria API's selectCase(), since a
 * plain Mongo Query has no equivalent of a computed sort key.
 *
 * Strategy: inject a synthetic field via $addFields that's 1 when the sort
 * field is null OR (for strings) an empty string, then sort ascending on
 * that synthetic field first (so blanks always land last regardless of
 * ASC/DESC on the real field), then sort on the real field.
 *
 * IMPORTANT: these build real AggregationOperation pipeline stages
 * (raw {"$addFields": {...}} / {"$sort": {...}} documents), NOT
 * AggregationExpression — those are a different interface (an expression
 * evaluates to a *value* used inside a stage; an operation IS a stage).
 * Mixing them up causes a ClassCastException at runtime, since Kotlin's
 * SAM conversion happily creates whichever interface you declare the
 * return type as, with no compile-time signal that they're incompatible.
 */
object SortBuilder {

    enum class Direction { ASC, DESC }

    data class SortSpec(
        val field: String,
        val direction: Direction = Direction.DESC
    )

    const val NULLS_LAST_FIELD = "__kraftadmin_sort_blank"

    fun from(field: String?, direction: String?): SortSpec? {
        field ?: return null
        val dir = when (direction?.uppercase()) {
            "ASC" -> Direction.ASC
            else -> Direction.DESC
        }
        return SortSpec(field, dir)
    }

    /**
     * Builds the $addFields stage that computes the nulls-last flag.
     * Treats null AND empty-string as "blank", matching JPA's SortBuilder.
     */
    fun addFieldsStage(spec: SortSpec?, allowedFields: List<String>): AggregationOperation? {
        if (spec == null || spec.field !in allowedFields) return null

        return AggregationOperation { _: AggregationOperationContext ->
            Document(
                "\$addFields",
                Document(
                    NULLS_LAST_FIELD,
                    Document(
                        "\$switch",
                        Document(
                            "branches", listOf(
                                Document(
                                    "case", Document(
                                        "\$or", listOf(
                                            Document("\$eq", listOf("$${spec.field}", null)),
                                            Document("\$eq", listOf("$${spec.field}", ""))
                                        )
                                    )
                                ).append("then", 1)
                            )
                        ).append("default", 0)
                    )
                )
            )
        }
    }

    /**
     * Builds the $sort stage: nulls-last flag ascending (blanks always
     * last), then the real field in the requested direction.
     */
    fun sortStage(spec: SortSpec?, allowedFields: List<String>): AggregationOperation? {
        if (spec == null || spec.field !in allowedFields) return null

        val direction = when (spec.direction) {
            Direction.ASC -> 1
            Direction.DESC -> -1
        }

        return AggregationOperation { _: AggregationOperationContext ->
            Document(
                "\$sort",
                Document(NULLS_LAST_FIELD, 1).append(spec.field, direction)
            )
        }
    }

    /** Plain Sort fallback for callers that don't need nulls-last aggregation semantics. */
    fun toSort(spec: SortSpec?, allowedFields: List<String>): Sort? {
        if (spec == null || spec.field !in allowedFields) return null
        val direction = when (spec.direction) {
            Direction.ASC -> Sort.Direction.ASC
            Direction.DESC -> Sort.Direction.DESC
        }
        return Sort.by(direction, spec.field)
    }
}