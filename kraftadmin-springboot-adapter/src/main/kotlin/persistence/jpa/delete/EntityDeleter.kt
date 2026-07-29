package persistence.jpa.delete

import com.kraftadmin.api.responses.KraftOperationResponse
import com.kraftadmin.context.KraftAdminContextHolder
import com.kraftadmin.events.KraftAdminEvent
import com.kraftadmin.events.KraftLifecycleService
import com.kraftadmin.utils.files.AdminStorageProvider
import com.kraftadmin.logging.KraftAdminLogging
import com.kraftadmin.model.BulkDeleteOutcome
import jakarta.persistence.EntityManager
import org.springframework.transaction.support.TransactionTemplate
import persistence.error.PersistenceErrorResolver
import persistence.error.PersistenceException
import persistence.jpa.metadata.JpaEntityMetadata
import kotlin.reflect.KClass

/**
 * Handles  delete operations.
 */
class EntityDeleter<T : Any>(
    private val entityClass: KClass<T>,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    private val metadata: JpaEntityMetadata<T>,
    adminStorageProvider: AdminStorageProvider,
    private val lifecycle: KraftLifecycleService,
    private val errorResolver: PersistenceErrorResolver
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    private val fileCleanupService = FileCleanupService(adminStorageProvider)

    fun delete(id: String): KraftOperationResponse<Unit> {

        val entity = try {
            transactionTemplate.execute {
                val convertedId = metadata.convertId( id)
                entityManager.find(entityClass.java, convertedId)
            }
        } catch (e: Exception) {
            // Failure at the FETCH stage (e.g. schema mismatch on a lazy
            // collection, connection drop) is a real infrastructure error,
            // not a "not found" — surface it as such.
            logger.error("Delete lookup failed for ${entityClass.simpleName}#$id", e)
            throw PersistenceException(
                errorResolver.resolve(entityClass.simpleName ?: "Resource", e),
                e
            )
        } ?: return KraftOperationResponse(
            false,
            "${entityClass.simpleName} not found."
        )

        val context = KraftAdminContextHolder.adminContext()

        lifecycle.onBeforeDelete(
            KraftAdminEvent.BeforeDelete(
                resourceName = entity.javaClass.simpleName,
                entity = entity,
                id = id,
                context = context
            )
        )

        var deleteException: Exception? = null

        val success = transactionTemplate.execute { status ->

            try {

                fileCleanupService.cleanupFiles(entity)

                entityManager.remove(
                    if (entityManager.contains(entity))
                        entity
                    else
                        entityManager.merge(entity)
                )

                entityManager.flush()

                true

            } catch (e: Exception) {

                logger.error(
                    "Delete failed for ${entityClass.simpleName}#$id",
                    e
                )

                status.setRollbackOnly()
                deleteException = e

                lifecycle.onDeleteFailed(
                    KraftAdminEvent.DeleteFailed(
                        resourceName = entity.javaClass.simpleName,
                        entity = entity,
                        id = id,
                        context = context,
                        exception = e,
                    )
                )

                false
            }

        } ?: false

        if (!success) {
            val cause = deleteException
            if (cause != null) {
                // Real failure (e.g. FK constraint blocking delete) — throw so
                // the caller gets the structured, resolved error instead of a
                // flattened generic string.
                throw PersistenceException(
                    errorResolver.resolve(entityClass.simpleName ?: "Resource", cause),
                    cause
                )
            }
            return KraftOperationResponse(
                false,
                "Failed to delete ${entityClass.simpleName}."
            )
        }

        lifecycle.onAfterDelete(
            KraftAdminEvent.AfterDelete(
                resourceName = entity.javaClass.simpleName,
                entity = entity,
                id = id,
                context = context
            )
        )

        return KraftOperationResponse(
            true,
            message = "${entityClass.simpleName} deleted successfully."
        )
    }

    /**
     * Deletes each id independently via [delete], so a constraint violation
     * on one record (e.g. Album#100 still has Tracks) is reported and
     * skipped rather than rolling back every other successful delete in
     * the batch. Reuses the exact same lifecycle hooks, file cleanup, and
     * error resolution as a single delete — no duplicated remove logic.
     */
    fun bulkDelete(ids: List<String>): BulkDeleteOutcome {
        val failed = linkedMapOf<String, String>()
        var deletedCount = 0

        ids.forEach { id ->
            try {
                val result = delete(id)
                if (result.success) {
                    deletedCount++
                } else {
                    failed[id] = result.message ?: "Delete failed."
                }
            } catch (e: PersistenceException) {
                logger.warn("Bulk delete failed for ${entityClass.simpleName}#$id: ${e.message}")
                failed[id] = e.message ?: "Delete failed due to a constraint violation."
            }
        }

        return BulkDeleteOutcome(
            requested = ids.size,
            deleted = deletedCount,
            failed = failed
        )
    }

}