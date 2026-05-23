package config

import analytics.AnalyticsProvider
import analytics.LocalAnalyticsProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import telemetry.SQLiteTelemetryProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnExpression(
    "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
)
class KraftAnalyticsConfiguration {

    @Bean
    fun analyticsProvider(sqliteProvider: SQLiteTelemetryProvider): AnalyticsProvider {
        // This satisfies the dependency for the controller
        return LocalAnalyticsProvider(sqliteProvider)
    }

}

