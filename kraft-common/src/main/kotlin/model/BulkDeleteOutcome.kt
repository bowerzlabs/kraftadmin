package com.kraftadmin.model

/**
 * Result of a bulk delete pass. Each id is deleted in its own transaction
 * (via [EntityDeleter.delete]) so that one undeletable row — e.g. blocked
 * by a foreign key constraint — doesn't roll back the entire batch.
 */
data class BulkDeleteOutcome(
    val requested: Int,
    val deleted: Int,
    val failed: Map<String, String>
)