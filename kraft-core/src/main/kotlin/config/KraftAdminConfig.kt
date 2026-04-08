package com.kraftadmin.config

import com.kraftadmin.security.KraftSecurityConfig
import com.kraftadmin.spi.KraftAdminResource

data class KraftAdminConfig(
    val port: Int = 8090,
    val basePath: String = "/admin",
    val title: String = "KraftAdmin",
    val mode: Mode = Mode.RUNTIME,
    val environment: Environment = Environment.PROD,
    val discoveredEntities: Set<Class<*>> = setOf(),
    val generatedResources: List<KraftAdminResource<*>> = listOf(),
//    val persistence: PersistenceConfig = PersistenceConfig.None,
    val security: KraftSecurityConfig = KraftSecurityConfig.Standalone(),
//    val ui: UiConfig = UiConfig(),
//    val features: Set<Feature> = emptySet(),
//    val metadataProviders: List<MetadataProvider> = emptyList()
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

