package com.kraftadmin.annotations

/**
 * Marks a class as a manageable KraftAdmin resource.
 * * This annotation is the entry point for the KraftAdmin engine. Any class decorated with
 * @KraftAdminResource will be automatically scanned and exposed via the Admin UI,
 * provided it is registered within the Runtime Config.
 *
 * @property label The human-readable name of the resource. If blank, the engine
 * infers the name from the class (e.g., "UserAccount" -> "User Account").
 * @property group Categorizes the resource in the sidebar. Resources with the same group
 * are clustered together under a collapsible header.
 * @property icon The visual identifier in the sidebar. Supports Unicode emojis or
 * supported icon library strings (e.g., "users", "settings").
 * @property hidden If true, the resource exists in the API but is omitted from the
 * sidebar navigation. Useful for internal configuration tables.
 * @property searchable Enables or disables the global text search bar for this resource
 * in the table view.
 * @property defaultSort Defines the initial data ordering.
 * Syntax: "fieldName:DIRECTION" (e.g., "id:DESC").
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KraftAdminResource(
    val label: String = "",
    val group: String = "Main",
    val icon: String = "📁",
    val hidden: Boolean = false,
    val searchable: Boolean = true,
    val defaultSort: String = ""
)