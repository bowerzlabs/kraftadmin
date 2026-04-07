package com.kraftadmin.ui_descriptors

import com.kraftadmin.api.responses.AdminUserDTO
import com.kraftadmin.security.AdminPrincipal
import config.FeatureConfig
import config.LocaleConfig
import config.PaginationConfig

data class KraftAdminDescriptor(
    val basePath: String,
    val title: String,
    val environment: EnvironmentDescriptor, // Changed from String to Object
    val currentUser: AdminUserDTO? = null,
    val resources: List<ResourceDescriptor>
)

//data class EnvironmentDescriptor(
//    val name: String,
//    val authMode: String,      // "bridge" or "standalone"
//    val showLogout: Boolean,   // Direct flag for UI simplicity
//    val version: String = "1.0.0"
//)

data class EnvironmentDescriptor(
    val name: String,
    val authMode: String,        // "bridge" or "standalone"
    val showLogout: Boolean,
    val version: String = "0.0.1",
    val theme: ThemeDescriptor,   // Colors and Dark Mode
    val features: FeatureConfig,  // Global UI Toggles
    val pagination: PaginationConfig, // Page size limits
    val locale: LocaleConfig      // Language/Timezone
)

data class ThemeDescriptor(
    val primaryColor: String,
    val darkMode: Boolean,
    val logoUrl: String?
)
