package com.kraftadmin.config

import com.kraftadmin.security.KraftSecurityConfig
import com.kraftadmin.spi.DiscoveredEntity
import com.kraftadmin.spi.KraftAdminResource

data class KraftAdminConfig(
    val port: Int = 8090,
    val basePath: String = "/admin",
    val title: String = "KraftAdmin",
    val mode: Mode = Mode.RUNTIME,
    val environment: Environment = Environment.PROD,
    val discoveredEntities: Set<DiscoveredEntity<*>> = setOf(),
    val generatedResources: List<KraftAdminResource<*>> = listOf(),
    val security: KraftSecurityConfig = KraftSecurityConfig.Standalone(),
) {

    enum class Mode {
        RUNTIME,   // running inside a server
        CLI        // dev tool usage
    }

    enum class Environment {
        DEV,
        PROD
    }

    override fun toString(): String {
        return "KraftAdminConfig(port=$port, basePath='$basePath', title='$title', discoverEntities=$discoveredEntities, generatedResources=$generatedResources)," +
                "kraftSecurityConfig=$security)"
    }
}

