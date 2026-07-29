package persistence.jpa.bulk_actions.export

data class ExportResult(
    val fileName: String,
    val contentType: String,
    val content: ByteArray
)