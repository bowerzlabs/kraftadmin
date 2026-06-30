package com.kraftadmin.domain.event

import com.kraftadmin.annotations.FileConfig
import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.domain.base.BaseEntity
import com.kraftadmin.enums.FormInputType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Entity
class Sponsor(
    var name: String = "",
    var website: String = "",
    @KraftAdminField(
        inputType = FormInputType.FILE,
        fileConfig = FileConfig(
            multiple = false
        )
    )
    var logoUrl: String? = null,
    @Enumerated(EnumType.STRING)
    var level: SponsorshipLevel = SponsorshipLevel.BRONZE
) : BaseEntity()