package com.kraftadmin.domain.event

import jakarta.persistence.Embeddable

@Embeddable
class Location(
    var city: String = "",
    var country: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0
)