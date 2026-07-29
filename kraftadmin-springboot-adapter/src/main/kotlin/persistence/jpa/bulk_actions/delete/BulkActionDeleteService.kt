package persistence.jpa.bulk_actions.delete

import com.kraftadmin.model.BulkDeleteOutcome
import com.kraftadmin.ui_descriptors.KraftAdminDescriptorFactory
import persistence.jpa.bulk_actions.BulkDeleteResult

class BulkDeleteService(
    private val descriptorFactory: KraftAdminDescriptorFactory,
) {
    fun bulkDelete(resource: String, selectedIds: List<String>): BulkDeleteResult {
        val outcome: BulkDeleteOutcome = descriptorFactory.bulkDeleteResource(resource, selectedIds)
        return BulkDeleteResult(
            requested = outcome.requested,
            deleted = outcome.deleted,
            failed = outcome.failed
        )
    }

}