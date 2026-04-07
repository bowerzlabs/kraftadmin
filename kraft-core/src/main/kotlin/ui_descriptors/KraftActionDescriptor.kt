package com.kraftadmin.ui_descriptors

data class KraftActionDescriptor(
    val name: String,        // Internal key (e.g., "sync-calendar")
    val label: String,       // Text on button
    val icon: String?,       // Icon name for Svelte (Lucide/Tabler)
    val variant: String,     // "primary", "danger", etc.
    val isRowAction: Boolean = true // Does it act on a specific ID or the whole table?
)