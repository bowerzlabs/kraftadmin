package com.kraftadmin

import com.kraftadmin.domain.event.Venue
import com.kraftadmin.utils.custom_actions.KraftActionHandler
import com.kraftadmin.utils.custom_actions.KraftActionResponse
import org.springframework.stereotype.Component

@Component
class VenueSyncHandler(
//    private val emailService: EmailService, // Injected via Spring
//    private val venueRepository: VenueRepository
) : KraftActionHandler<Venue> {

    override fun execute(entity: Any?, params: Map<String, Any?>): KraftActionResponse {
        // 1. Do heavy lifting
//        emailService.sendAdminAlert("Sync started for ${entity.name}")

        // 2. Return result to the Svelte UI
        return KraftActionResponse(success = true, message = "Calendar sync triggered!")
    }
}

