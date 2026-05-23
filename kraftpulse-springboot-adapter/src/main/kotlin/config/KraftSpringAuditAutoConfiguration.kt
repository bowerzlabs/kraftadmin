package config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import util.KraftSpringLoggingAuditor
import util.KraftSpringLoggingService
import logging.KraftAdminAuditor
import logging.NoOpKraftAuditor
import telementary.KraftTelemetryService
import telemetry.SQLiteTelemetryProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean

//@AutoConfiguration
//class KraftSpringAuditAutoConfiguration(
//    private val sqLiteTelemetryProvider: SQLiteTelemetryProvider,
//    private val applicationEventPublisher: ApplicationEventPublisher
//) {
//
////    @Bean
////    @ConditionalOnMissingBean
////    fun kraftSpringLoggingService(): KraftSpringLoggingService {
////        return KraftSpringLoggingService(
////            sqLiteTelemetryProvider,
////            applicationEventPublisher = applicationEventPublisher
////        )
////    }
//
//    @Bean
//    @ConditionalOnMissingBean
//    @ConditionalOnExpression(
//        "\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}"
//    )
//    fun kraftSpringLoggingService(sqLiteTelemetryProvider: SQLiteTelemetryProvider): KraftSpringLoggingService {
//        return KraftSpringLoggingService(
//            sqLiteTelemetryProvider,
//            applicationEventPublisher = applicationEventPublisher
//        )
//    }
//
//    @Bean
//    @ConditionalOnMissingBean(KraftAdminAuditor::class)
//    @ConditionalOnExpression("\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}")
//    fun kraftSpringLoggingAuditor(telemetryService: KraftTelemetryService): KraftAdminAuditor {
//        val factory = LoggerFactory.getILoggerFactory()
//        if (factory is LoggerContext) {
//            factory.getLogger("KRAFT_ADMIN_AUDIT").level = Level.INFO
//        }
//        // This links the Business Auditor to your SQLite-backed Telemetry Service
//        return KraftSpringLoggingAuditor(telemetryService)
//    }
//
//    // 2. The FALLBACK auditor (if the real one isn't loaded)
//    @Bean
//    @ConditionalOnMissingBean(KraftAdminAuditor::class)
//    fun noOpKraftAdminAuditor(): KraftAdminAuditor = NoOpKraftAuditor()
//
//}


@AutoConfiguration
// REMOVED class-level @ConditionalOnExpression here
class KraftSpringAuditAutoConfiguration(
    // Using ObjectProvider makes these dependencies optional if the Telemetry engine is off
    private val sqLiteTelemetryProvider: ObjectProvider<SQLiteTelemetryProvider>,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    @Bean
    @ConditionalOnMissingBean
    // Only load the service if telemetry is enabled
    @ConditionalOnExpression("\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}")
    fun kraftSpringLoggingService(): KraftSpringLoggingService {
        return KraftSpringLoggingService(
            sqLiteTelemetryProvider.getIfAvailable()!!,
            applicationEventPublisher = applicationEventPublisher
        )
    }

    @Bean
    @ConditionalOnMissingBean(KraftAdminAuditor::class)
    // Only load the REAL auditor if telemetry is enabled
    @ConditionalOnExpression("\${kraftpulse.enabled:false} and \${kraftpulse.telemetry-config.enabled:false}")
    fun kraftSpringLoggingAuditor(telemetryService: KraftTelemetryService): KraftAdminAuditor {
        val factory = LoggerFactory.getILoggerFactory()
        if (factory is LoggerContext) {
            factory.getLogger("KRAFT_ADMIN_AUDIT").level = Level.INFO
        }
        return KraftSpringLoggingAuditor(telemetryService)
    }

    @Bean
    @ConditionalOnMissingBean(KraftAdminAuditor::class)
    // This bean is now VISIBLE even if telemetry is off,
    // but @ConditionalOnMissingBean ensures it only loads if the one above didn't.
    fun noOpKraftAdminAuditor(): KraftAdminAuditor = NoOpKraftAuditor()
}
