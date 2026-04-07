package query

data class QuerySpec(
    val page: Int = 0,
    val size: Int = 20,
    val sort: List<SortSpec> = emptyList(),
    val filters: Map<String, Any?> = emptyMap(),
    val search: String? = null
)

data class SortSpec(
    val field: String,
    val direction: SortDirection
)

enum class SortDirection {
    ASC, DESC
}
