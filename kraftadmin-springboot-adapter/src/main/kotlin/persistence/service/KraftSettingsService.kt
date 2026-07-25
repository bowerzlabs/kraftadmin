package persistence.service

import com.kraftadmin.logging.KraftAdminLogging
import config.KraftAdminProperties
import dtos.PublicKraftAdminSettings
import dtos.SettingsUpdateRequest
import jakarta.annotation.PostConstruct
import json.KraftJsonSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import util.toPublicSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Service
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftSettingsService(
    private val properties: KraftAdminProperties,
    private val serializer: KraftJsonSerializer,
    @param:Value("\${kraftadmin.settings-file:kraft-settings.json}") private val fileName: String
) {
    private val logger = KraftAdminLogging.logger(javaClass)

    private val settingsFile: File = File(System.getProperty("user.dir"), fileName)

    @PostConstruct
    fun init() {

        if (settingsFile.exists()) {
            try {
                val overrides = serializer.fromJson(settingsFile.readText(), SettingsUpdateRequest::class.java)
                applyUpdate(overrides)
            } catch (e: Exception) {
                logger.error("Failed to load existing Kraft settings from ${settingsFile.name}", e)
            }
        } else {
            try {
                saveToFile()
            } catch (e: Exception) {
                logger.error("Could not bootstrap default settings file", e)
            }
        }
    }

    private fun saveToFile() {
        val snapshot = toUpdateSnapshot()
        val json = serializer.toJson(snapshot)

        val tempFile = File(settingsFile.parentFile ?: File("."), "${settingsFile.name}.tmp")
        tempFile.writeText(json)
        Files.move(tempFile.toPath(), settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)

        val writtenBack = settingsFile.readText()
        if (writtenBack.trim() != json.trim()) {
            logger.warn("KraftAdmin: settings file content mismatch after write at {}", settingsFile.absolutePath)
        }
    }

    private fun toUpdateSnapshot(): SettingsUpdateRequest {
        val update = SettingsUpdateRequest()

        update.title = properties.title
        update.logoUrl = properties.logoUrl

        update.theme = SettingsUpdateRequest.ThemeUpdate().apply {
            primaryColor = properties.theme.primaryColor
            darkMode = properties.theme.darkMode
        }

        update.storage = SettingsUpdateRequest.StorageUpdate().apply {
            uploadDir = properties.storage.uploadDir
            publicUrlPrefix = properties.storage.publicUrlPrefix
        }

        update.pagination = SettingsUpdateRequest.PaginationUpdate().apply {
            defaultPageSize = properties.pagination.defaultPageSize
            maxPageSize = properties.pagination.maxPageSize
        }

        update.features = SettingsUpdateRequest.FeatureUpdate().apply {
            allowDelete = properties.features.allowDelete
            showTimestamps = properties.features.showTimestamps
            readOnly = properties.features.readOnly
        }

        update.localeConfig = SettingsUpdateRequest.LocaleUpdate().apply {
            defaultLanguage = properties.localeConfig.defaultLanguage
            timezone = properties.localeConfig.timezone
        }

        update.telemetryConfig = SettingsUpdateRequest.TelemetryUpdate().apply {
            cloudUrl = properties.telemetryConfig.cloudUrl
            enabled = properties.telemetryConfig.enabled
        }

        return update
    }

    fun updateSettings(request: SettingsUpdateRequest): PublicKraftAdminSettings {
        applyUpdate(request)
        try {
            saveToFile()
            logger.info("Successfully persisted settings to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to write settings update to disk at {}", e)
            throw IllegalStateException("Settings applied in memory but could not be saved: ${e.message}", e)
        }
        return properties.toPublicSettings()
    }
    
    fun getPublicSettings(): PublicKraftAdminSettings = properties.toPublicSettings()

    private fun applyUpdate(source: SettingsUpdateRequest) {
        source.title?.takeIf { it.isNotBlank() }?.let { properties.title = it }
        source.logoUrl?.let { properties.logoUrl = it.ifBlank { null } }
        source.theme?.primaryColor?.takeIf { it.isNotBlank() }?.let { properties.theme.primaryColor = it }
        source.theme?.darkMode?.let { properties.theme.darkMode = it }
        source.storage?.uploadDir?.takeIf { it.isNotBlank() }?.let { properties.storage.uploadDir = it }
        source.storage?.publicUrlPrefix?.takeIf { it.isNotBlank() }?.let { properties.storage.publicUrlPrefix = it }
        source.pagination?.defaultPageSize?.takeIf { it > 0 }?.let { properties.pagination.defaultPageSize = it }
        source.pagination?.maxPageSize?.takeIf { it > 0 }?.let { properties.pagination.maxPageSize = it }
        source.features?.allowDelete?.let { properties.features.allowDelete = it }
        source.features?.showTimestamps?.let { properties.features.showTimestamps = it }
        source.features?.readOnly?.let { properties.features.readOnly = it }
        source.localeConfig?.defaultLanguage?.takeIf { it.isNotBlank() }?.let { properties.localeConfig.defaultLanguage = it }
        source.localeConfig?.timezone?.takeIf { it.isNotBlank() }?.let { properties.localeConfig.timezone = it }
        source.telemetryConfig?.cloudUrl?.takeIf { it.isNotBlank() }?.let { properties.telemetryConfig.cloudUrl = it }
        source.telemetryConfig?.enabled?.let { properties.telemetryConfig.enabled = it }
    }
}
