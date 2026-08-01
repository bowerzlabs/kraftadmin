package persistence.mongo.query

import org.springframework.data.mongodb.core.query.Query
import kotlin.math.ceil

/**
 * Applies pagination (skip + limit) to a Mongo Query.
 * Mirrors persistence.jpa.query.PageableBuilder — same PageSpec shape,
 * different underlying query type (Mongo Query instead of TypedQuery).
 */
object PageableBuilder {

    data class PageSpec(
        val page: Int,      // 1-based
        val size: Int,
        val maxSize: Int = 100
    ) {
        val effectivePage: Int = page.coerceAtLeast(1)
        val effectiveSize: Int = size.coerceAtLeast(1).coerceAtMost(maxSize)
        val offset: Long = ((effectivePage - 1) * effectiveSize).toLong()
    }

    fun apply(query: Query, spec: PageSpec): Query {
        query.skip(spec.offset)
        query.limit(spec.effectiveSize)
        return query
    }

    fun totalPages(total: Long, size: Int): Int {
        if (total == 0L) return 0
        return ceil(total.toDouble() / size).toInt()
    }
}