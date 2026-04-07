package com.kraftadmin.domain.event

import com.kraftadmin.annotations.KraftAdminLookup
import com.kraftadmin.domain.base.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class ScheduleItem(
    var activityName: String = "",
    var startTime: LocalDateTime = LocalDateTime.now(),

    @ManyToOne
    @JoinColumn(name = "event_id")
    @KraftAdminLookup(lookupKey ="title", displayField = "title", resource = Event::class)
    var event: Event? = null
) : BaseEntity()