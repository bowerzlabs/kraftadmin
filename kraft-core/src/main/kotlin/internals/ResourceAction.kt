package com.kraftadmin.internals

data class ResourceAction(
    val name: String,        // e.g., "Send Welcome Email"
    val slug: String,        // e.g., "send-email"
    val icon: String?,       // e.g., "mail"
    val variant: String = "primary" // "danger" for red buttons
)
