package com.kraftadmin.config

import com.kraftadmin.security.BasicAuthConfig
import config.FeatureConfig
import config.KraftAdminPropertiesConfig
import config.LocaleConfig
import config.PaginationConfig
import config.SecurityConfig
import config.StorageConfig
import config.TelemetryConfig
import config.ThemeConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "kraftadmin")
data class SpringKraftAdminProperties(
    override var basePath: String = "/admin",
    override var title: String = "KraftAdmin",
    override var logoUrl: String? = null,
    @NestedConfigurationProperty
    override var theme: SpringThemeProperties = SpringThemeProperties(),
    @NestedConfigurationProperty
    override var storage: SpringStorageProperties = SpringStorageProperties(),
    @NestedConfigurationProperty
    override var security: SpringSecurityProperties = SpringSecurityProperties(),
    @NestedConfigurationProperty
    override var pagination: SpringPaginationProperties = SpringPaginationProperties(),
    @NestedConfigurationProperty
    override var features: SpringFeatureProperties = SpringFeatureProperties(),
    @NestedConfigurationProperty
    override var localeConfig: SpringLocaleProperties = SpringLocaleProperties(),
    @NestedConfigurationProperty
    override var telemetryConfig: TelemetryProperties = TelemetryProperties()
) : KraftAdminPropertiesConfig {
    
    data class SpringThemeProperties(
        override var primaryColor: String = "#3b82f6", // Default Blue
        override var darkMode: Boolean = true
    ) : ThemeConfig

    data class SpringStorageProperties(
        override var uploadDir: String = "uploads/admin",
        override var publicUrlPrefix: String = "/admin/files"
    ) : StorageConfig

    data class SpringSecurityProperties(
        override var cookieName: String = "KRAFT_SESSION",
        override var sessionExpiryMinutes: Long = 60,
        override var requiredRoles: Set<String> = setOf("ROLE_ADMIN"),
        override var protectedRoutes: Map<String, Set<String>> = emptyMap(),
        override var basicAuth: BasicAuthConfig = BasicAuthConfig()
    ) : SecurityConfig

    data class SpringPaginationProperties(
        override var defaultPageSize: Int = 20,
        override var maxPageSize: Int = 100
    ) : PaginationConfig

    data class SpringFeatureProperties(
        override var allowDelete: Boolean = true,
        override var showTimestamps: Boolean = true,
        override var readOnly: Boolean = false
    ) : FeatureConfig

    data class SpringLocaleProperties(
        override var defaultLanguage: String = "en",
        override var timezone: String = "UTC"
    ) : LocaleConfig

    data class TelemetryProperties(
        override var cloudUrl: String = "http://localhost:8090", // Your Ktor/Rust Sink
        override var enabled: Boolean = true
    ) : TelemetryConfig
}