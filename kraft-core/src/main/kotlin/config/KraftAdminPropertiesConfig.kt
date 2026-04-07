package config

import com.kraftadmin.security.BasicAuthConfig

interface KraftAdminPropertiesConfig {
    val basePath: String
    val title: String
    val logoUrl: String?         // Brand the sidebar/login
    val theme: ThemeConfig       // Control colors without CSS hacking
    val storage: StorageConfig
    val security: SecurityConfig
    val pagination: PaginationConfig
    val features: FeatureConfig   // Toggle "Dangerous" buttons globally
    val localeConfig: LocaleConfig
    val telemetryConfig: TelemetryConfig
}

interface ThemeConfig {
    val primaryColor: String     // hex code like "#3b82f6"
    val darkMode: Boolean        // force dark, light, or auto
}

interface FeatureConfig {
    val allowDelete: Boolean     // Global kill-switch for "Delete" buttons
    val showTimestamps: Boolean  // Toggle createdAt/updatedAt visibility
    val readOnly: Boolean        // Turn the whole admin into a "Viewer"
}

interface StorageConfig {
    val uploadDir: String
    val publicUrlPrefix: String
}

interface SecurityConfig {
    val cookieName: String
    val sessionExpiryMinutes: Long

    /** * Global roles allowed to access the Admin UI.
     * If empty, any authenticated user can enter.
     */
    val requiredRoles: Set<String>

    /** * Specific path-to-role mappings for granular control.
     * Example: "/api/resources/User" -> setOf("ROLE_SUPER_ADMIN")
     */
    val protectedRoutes: Map<String, Set<String>>
    val basicAuth: BasicAuthConfig
}

interface PaginationConfig {
    val defaultPageSize: Int
    val maxPageSize: Int
}

interface LocaleConfig {
    val defaultLanguage: String // e.g., "en"
    val timezone: String        // e.g., "UTC" or "Africa/Nairobi"
}

interface TelemetryConfig {
    var cloudUrl: String
    var enabled: Boolean
}
