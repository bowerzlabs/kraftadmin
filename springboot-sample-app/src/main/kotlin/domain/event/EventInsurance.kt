package com.kraftadmin.domain.event

import com.kraftadmin.domain.base.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.OneToOne

@Entity
class EventInsurance(
    var provider: String = "",
    var policyNumber: String = "",
    var coverageAmount: Double = 0.0,
    var validity: Int = 0
) : BaseEntity()

