package persistence.mongo.delete

import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.logging.KraftAdminLogging
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.reflect.KClass

class DocumentDeleter<T : Any>(
    private val entityClass: KClass<T>,
    private val mongoTemplate: MongoTemplate
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    fun delete(id: String, existing: T?): KraftOperationResponse<Unit> {
        if (existing == null) {
            return KraftOperationResponse(false, "Document not found")
        }

        return try {
            mongoTemplate.remove(existing)
            KraftOperationResponse(true)
        } catch (e: Exception) {
            logger.error("delete failed for ${entityClass.simpleName} #$id: ${e.message}", e)
            KraftOperationResponse(false, e.message ?: "Failed to delete document")
        }
    }
}