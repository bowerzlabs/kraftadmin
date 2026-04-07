package com.kraftadmin.domain.event

import com.kraftadmin.annotations.KraftAdminField
import com.kraftadmin.annotations.KraftAdminLookup
import com.kraftadmin.domain.base.BaseEntity
import com.kraftadmin.enums.FormInputType
import jakarta.persistence.*
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate
import java.time.LocalTime

@Entity
class Event(

    @field:NotEmpty(message = "Event title is required")
    var title: String = "",

    @KraftAdminField(inputType = FormInputType.EMAIL)
    var contactEmail: String? = null,

    @KraftAdminField(inputType = FormInputType.TEL)
    var contactPhone: String? = null,

    @KraftAdminField(inputType = FormInputType.URL)
    var websiteUrl: String? = null,

    @KraftAdminField(inputType = FormInputType.COLOR)
    var themeColor: String = "#3b82f6",

    @KraftAdminField(inputType = FormInputType.RANGE)
    @Column(name = "intensity_level")
    var excitementLevel: Int = 50, // Test range 0-100

    @KraftAdminField(inputType = FormInputType.TIME)
    var dailyStartTime: LocalTime? = null,

    @KraftAdminField(inputType = FormInputType.DATE)
    var deadlineDate: LocalDate? = null,

    @KraftAdminField(inputType = FormInputType.WYSIWYG)
    @Lob
    var termsAndConditions: String? = null,

    @Enumerated(EnumType.STRING)
    @KraftAdminField(inputType = FormInputType.SELECT)
    var priority: EventPriority = EventPriority.MEDIUM,

    // --- NEW: MAP HANDLING ---
    @ElementCollection
    @CollectionTable(name = "event_metadata")
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    var metadata: MutableMap<String, String> = mutableMapOf(),

    // --- EMBEDDED (Value Object) ---
    // Tests recursive mapping of a non-entity object
    @Embedded
    var location: Location = Location(),

    // --- ONE-TO-ONE ---
    // Tests single-link relationship ownership
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "insurance_id")
    @KraftAdminLookup(displayField = "provider", lookupKey = "provider", resource = EventInsurance::class)
    var insurancePolicy: EventInsurance? = null,

    // --- ONE-TO-MANY ---
    // Tests a collection of entities owned strictly by this Event
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    var schedule: MutableList<ScheduleItem> = mutableListOf(),

    @ElementCollection
    @CollectionTable(name = "event_tags", joinColumns = [JoinColumn(name = "event_id")])
    @Column(name = "tag")
    var tags: Set<String> = mutableSetOf(),

    @ElementCollection
    @CollectionTable(name = "event_highlights")
    var highlights: List<String> = mutableListOf(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    @KraftAdminLookup(displayField = "name", lookupKey = "name", resource = Venue::class)
    var venue: Venue? = null,

    @ManyToMany
    @JoinTable(
        name = "event_sponsorships",
        joinColumns = [JoinColumn(name = "event_id")],
        inverseJoinColumns = [JoinColumn(name = "sponsor_id")]
    )
    @KraftAdminLookup(displayField = "name", lookupKey = "name", resource = Sponsor::class)
    var sponsors: MutableSet<Sponsor> = mutableSetOf()
) : BaseEntity()

enum class EventStatus { DRAFT, PUBLISHED, CANCELLED }
enum class EventPriority { LOW, MEDIUM, HIGH }