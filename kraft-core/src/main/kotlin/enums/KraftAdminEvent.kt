package com.kraftadmin.enums

/**
 * Lifecycle stages for KraftAdmin operations.
 */
enum class KraftAdminEvent {
        BEFORE_CREATE, AFTER_CREATE,
        BEFORE_UPDATE, AFTER_UPDATE,
        BEFORE_DELETE, AFTER_DELETE,
        BEFORE_FETCH,  AFTER_FETCH
}