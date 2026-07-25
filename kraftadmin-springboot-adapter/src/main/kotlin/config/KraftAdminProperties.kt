package config

import com.kraftadmin.BuildInfo
import com.kraftadmin.config.FeatureConfig
import com.kraftadmin.config.KraftAdminPropertiesConfig
import com.kraftadmin.config.LocaleConfig
import com.kraftadmin.config.LoggingConfig
import com.kraftadmin.config.PaginationConfig
import com.kraftadmin.config.SecurityConfig
import com.kraftadmin.config.StorageConfig
import com.kraftadmin.config.TelemetryConfig
import com.kraftadmin.config.TelemetryProvider
import com.kraftadmin.config.ThemeConfig
import com.kraftadmin.security.BasicAuthConfig
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "kraftadmin")
data class KraftAdminProperties(
    override var enabled: Boolean = false,
    override var basePath: String = "/admin",
    override var title: String = "KraftAdmin",
    override var logoUrl: String? = null,
    override val version: String = BuildInfo.VERSION,
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
    override var telemetryConfig: TelemetryProperties = TelemetryProperties(),
    @NestedConfigurationProperty
    override var loggingConfig: KraftAdminLoggingProperties =
        KraftAdminLoggingProperties()
) : KraftAdminPropertiesConfig {

    class SpringThemeProperties(
        override var primaryColor: String = "#3b82f6",
        override var darkMode: Boolean = true
    ) : ThemeConfig

    class SpringStorageProperties(
        override var uploadDir: String = "uploads/admin",
        override var publicUrlPrefix: String = "/admin/files"
    ) : StorageConfig

    class SpringSecurityProperties(
        override var cookieName: String = "KRAFTADMIN_SESSION",
        override var sessionExpiryMinutes: Long = 60,
        override var requiredRoles: List<String> = emptyList(),
        override var protectedRoutes: Map<String, Set<String>> = emptyMap(),
        @NestedConfigurationProperty
        override var basicAuth: BasicAuthConfig = BasicAuthConfig(),
        override var allowedUsers: Set<String> = emptySet(),
        override var userPermissions: Map<String, Set<String>> = emptyMap(),
    ) : SecurityConfig

    class SpringPaginationProperties(
        override var defaultPageSize: Int = 20,
        override var maxPageSize: Int = 100
    ) : PaginationConfig

    class SpringFeatureProperties(
        override var allowDelete: Boolean = true,
        override var showTimestamps: Boolean = true,
        override var readOnly: Boolean = false
    ) : FeatureConfig

    class SpringLocaleProperties(
        override var defaultLanguage: String = "en",
        override var timezone: String = "UTC"
    ) : LocaleConfig

    class TelemetryProperties(
        override var cloudUrl: String = "http://localhost:8090",
        override var enabled: Boolean = false,
        override var path: String? = ".kraft-telemetry.db",
        override var provider: TelemetryProvider = TelemetryProvider.LOCAL,
        override val apiKey: String? = null,
        override val secretKey: String? = null
    ) : TelemetryConfig


    data class KraftAdminLoggingProperties(
        override var enabled: Boolean = true
    ) : LoggingConfig

}