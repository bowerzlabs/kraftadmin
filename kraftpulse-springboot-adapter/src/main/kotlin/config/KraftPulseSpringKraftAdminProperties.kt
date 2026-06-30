package config

import com.kraftadmin.BuildInfo
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import security.BasicAuthConfig

@ConfigurationProperties(prefix = "kraftpulse")
data class KraftPulseSpringKraftAdminProperties(
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
    // ✅ New — controls whether/how KraftPulse pushes data into a MeterRegistry.
    // Entirely additive: defaults preserve current behavior (Micrometer bridge
    // enabled if a MeterRegistry bean happens to exist; consumer can disable
    // it explicitly, or scope which event types get pushed).
    @NestedConfigurationProperty
    var metricsConfig: MetricsProperties = MetricsProperties()
) : KraftAdminPropertiesConfig {

    init {
        println("kraftPulseSpringKraftAdminProperties: $this")
    }

    class SpringThemeProperties(
        override var primaryColor: String = "#3b82f6",
        override var darkMode: Boolean = true
    ) : ThemeConfig

    class SpringStorageProperties(
        override var uploadDir: String = "uploads/admin",
        override var publicUrlPrefix: String = "/admin/files"
    ) : StorageConfig

    class SpringSecurityProperties(
        override var cookieName: String = "KRAFT_SESSION",
        override var sessionExpiryMinutes: Long = 60,
        override var requiredRoles: Set<String> = setOf("ROLE_ADMIN"),
        override var protectedRoutes: Map<String, Set<String>> = emptyMap(),
        @NestedConfigurationProperty
        override var basicAuth: BasicAuthConfig = BasicAuthConfig()
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

}