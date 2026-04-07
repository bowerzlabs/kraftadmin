package com.kraftadmin.domain.event

import com.kraftadmin.VenueSyncHandler
import com.kraftadmin.annotations.KraftAdminCustomAction
import com.kraftadmin.domain.base.BaseEntity
import jakarta.persistence.Entity

@Entity
@KraftAdminCustomAction(
    name = "sync-calendar",
    label = "Sync Google Calendar",
    icon = "calendar",
    handler = VenueSyncHandler::class // Point to the logic
)
class Venue(
    var name: String = "",
    var capacity: Int = 0,
    var address: String = ""
) : BaseEntity()

