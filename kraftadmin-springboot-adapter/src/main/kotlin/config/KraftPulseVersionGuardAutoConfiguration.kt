package config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.SpringBootVersion
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.core.Ordered

@AutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
class KraftPulseVersionGuardAutoConfiguration {

    private val logger = LoggerFactory.getLogger(KraftPulseVersionGuardAutoConfiguration::class.java)

    @PostConstruct
    fun checkSupportedVersion() {
        val version = SpringBootVersion.getVersion() ?: return // Can't determine, assume compatibility

        val majorVersion = version.substringBefore(".").toIntOrNull() ?: return

        when {
            majorVersion < 3 -> {
                // Keep the hard block for extremely old 2.x versions due to javax -> jakarta namespace changes
                throw IllegalStateException(
                    """
                    KraftPulse/KraftAdmin: Unsupported Spring Boot version detected: $version
                    
                    This library requires the Jakarta EE namespace (Spring Boot 3.0+ / 4.0+).
                    Spring Boot 2.x and below utilize the legacy 'javax' namespace and are not supported.
                    """.trimIndent()
                )
            }
            majorVersion >= 4 -> {
                // Log an informational notice confirming compatibility verification for Spring Boot 4.x+
                logger.info("KraftPulse/KraftAdmin: Executing in Spring Boot 4.x environment ($version). Activation verified.")
            }
            else -> {
                // standard Spring Boot 3.x environment execution path
                logger.debug("KraftPulse/KraftAdmin: Executing in standard Spring Boot 3.x environment ($version).")
            }
        }
    }
}