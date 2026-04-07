package com.kraftadmin.persistence.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.kraftadmin.config.SpringKraftAdminProperties
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class KraftSettingsService(
    private val properties: SpringKraftAdminProperties, // From YAML
    private val objectMapper: ObjectMapper,             // Jackson from Spring
//    @Value("\${kraftadmin.settings-file:kraft-settings.json}") private val settingsFile: String
    @Value("\${kraftadmin.settings-file:kraft-settings.json}") private val fileName: String
) {
    private val logger = LoggerFactory.getLogger(KraftSettingsService::class.java)
//    private val file = File(settingsFile)
//
//    @PostConstruct
//    fun init() {
//        if (file.exists()) {
//            try {
//                val overrides = objectMapper.readValue(file, SpringKraftAdminProperties::class.java)
//                merge(overrides)
//                logger.info("KraftAdmin: UI Settings applied from $settingsFile")
//            } catch (e: Exception) {
//                logger.error("Failed to load Kraft settings file", e)
//            }
//        } else {
//            logger.info("KraftAdmin: No local overrides found ($settingsFile). Using defaults from application.yml")
//        }
//    }

    // Force the file to the current working directory (Project Root)
    private val settingsFile: File = File(System.getProperty("user.dir"), fileName)

    @PostConstruct
    fun init() {
        if (settingsFile.exists()) {
            try {
                // Read the file and update the Spring Bean state
                val overrides = objectMapper.readValue(settingsFile, SpringKraftAdminProperties::class.java)
                merge(overrides)
                logger.info("KraftAdmin: UI Settings synchronized from ${settingsFile.absolutePath}")
            } catch (e: Exception) {
                logger.error("Failed to load existing Kraft settings from ${settingsFile.name}", e)
            }
        } else {
            // FORCE CREATE: If it doesn't exist, generate the initial file using defaults
            // This prevents 404s from the UI because the file is now guaranteed to exist
            try {
                logger.info("KraftAdmin: No settings file found. Creating default at ${settingsFile.absolutePath}")
                saveToFile()
            } catch (e: Exception) {
                logger.error("Could not bootstrap default settings file", e)
            }
        }
    }

    /**
     * Persists the current state of the 'properties' bean to the root JSON file.
     */
    private fun saveToFile() {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile, properties)
    }

    fun updateSettings(newProps: SpringKraftAdminProperties): SpringKraftAdminProperties {
        // 1. Update in-memory bean
        merge(newProps)

        try {
            // 2. Persist to root
            saveToFile()
            logger.info("Successfully persisted settings to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to write settings update to disk", e)
        }

        return properties
    }

    /**
     * Called by UI Controller when settings are saved.
     * Returns the fully merged properties to ensure Frontend-Backend sync.
     */
//    fun updateSettings2(newProps: SpringKraftAdminProperties): SpringKraftAdminProperties {
//        logger.info("Updating settings. Current Title: ${properties.title}, New Title: ${newProps.title}")
//
//        // 1. Merge incoming UI changes into the Spring-managed bean
//        merge(newProps)
//
//        try {
//            // 2. Persist the merged state to the JSON file
//            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, properties)
//            logger.info("Successfully persisted merged settings to ${file.absolutePath}")
//        } catch (e: Exception) {
//            logger.error("Failed to write settings to $settingsFile", e)
//            // Even if file write fails, we return the in-memory properties so the UI stays functional
//        }
//
//        // 3. Return the Source of Truth
//        return properties
//    }


    open fun getCurrentProperties(): SpringKraftAdminProperties {
//        return objectMapper.readValue(file, SpringKraftAdminProperties::class.java)
        return properties
    }

    private fun merge(source: SpringKraftAdminProperties) {
        // 1. Root Level Fields - Only update if not blank
        if (source.basePath.isNotBlank()) properties.basePath = source.basePath
        if (source.title.isNotBlank()) properties.title = source.title
        source.logoUrl?.let { if (it.isNotBlank()) properties.logoUrl = it }

        // 2. Theme Settings
        if (source.theme.primaryColor.isNotBlank()) properties.theme.primaryColor = source.theme.primaryColor
        properties.theme.darkMode = source.theme.darkMode // Boolean usually safe to overwrite

        // 3. Storage Settings
        if (source.storage.uploadDir.isNotBlank()) properties.storage.uploadDir = source.storage.uploadDir
        if (source.storage.publicUrlPrefix.isNotBlank()) properties.storage.publicUrlPrefix = source.storage.publicUrlPrefix

        // 4. Security Settings
        if (source.security.cookieName.isNotBlank()) properties.security.cookieName = source.security.cookieName
        if (source.security.sessionExpiryMinutes > 0) properties.security.sessionExpiryMinutes = source.security.sessionExpiryMinutes
        if (source.security.requiredRoles.isNotEmpty()) properties.security.requiredRoles = source.security.requiredRoles
        if (source.security.protectedRoutes.isNotEmpty()) properties.security.protectedRoutes = source.security.protectedRoutes

        // 5. Pagination Settings
        if (source.pagination.defaultPageSize > 0) properties.pagination.defaultPageSize = source.pagination.defaultPageSize
        if (source.pagination.maxPageSize > 0) properties.pagination.maxPageSize = source.pagination.maxPageSize

        // 6. Feature Toggles
        properties.features.allowDelete = source.features.allowDelete
        properties.features.showTimestamps = source.features.showTimestamps
        properties.features.readOnly = source.features.readOnly

        // 7. Locale & Timezone
        if (source.localeConfig.defaultLanguage.isNotBlank()) properties.localeConfig.defaultLanguage = source.localeConfig.defaultLanguage
        if (source.localeConfig.timezone.isNotBlank()) properties.localeConfig.timezone = source.localeConfig.timezone

        // 8. Telemetry (The BI Heartbeat)
        if (source.telemetryConfig.cloudUrl.isNotBlank()) properties.telemetryConfig.cloudUrl = source.telemetryConfig.cloudUrl
        properties.telemetryConfig.enabled = source.telemetryConfig.enabled
    }

}