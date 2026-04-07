package com.kraftadmin.annotations


import com.kraftadmin.enums.KraftAdminEvent

/**
 * Marks a method as a listener for KraftAdmin lifecycle events.
 * * The annotated method should typically accept the entity being processed
 * and, in 'BEFORE' events, may throw exceptions to cancel the operation.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KraftAdminListener(
    /** The specific lifecycle event(s) to listen for. */
    val on: Array<KraftAdminEvent>,

    /** The resource name to filter for. If blank, listens to ALL resources. */
    val resource: String = ""
)