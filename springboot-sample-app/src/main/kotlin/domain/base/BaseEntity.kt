package com.kraftadmin.domain.base

import jakarta.persistence.*
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    open val id: String? = null,

    @Column(updatable = false)
    open var createdAt: LocalDateTime = LocalDateTime.now(),

    open var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() { updatedAt = LocalDateTime.now() }
}