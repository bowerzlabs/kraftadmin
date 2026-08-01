package util

import config.KraftAdminProperties
import dtos.PublicKraftAdminSettings

fun KraftAdminProperties.toPublicSettings(): PublicKraftAdminSettings {
    val publicSettings = PublicKraftAdminSettings()

    // Assign top-level properties
    publicSettings.basePath = this.basePath
    publicSettings.title = this.title
    publicSettings.logoUrl = this.logoUrl
    publicSettings.version = this.version

    // Assign nested object properties
    publicSettings.theme.apply {
        primaryColor = this@toPublicSettings.theme.primaryColor
        darkMode = this@toPublicSettings.theme.darkMode
    }

    publicSettings.storage.apply {
        uploadDir = this@toPublicSettings.storage.uploadDir
        publicUrlPrefix = this@toPublicSettings.storage.publicUrlPrefix
    }

    publicSettings.security.apply {
        cookieName = this@toPublicSettings.security.cookieName
        sessionExpiryMinutes = this@toPublicSettings.security.sessionExpiryMinutes
    }

    publicSettings.pagination.apply {
        defaultPageSize = this@toPublicSettings.pagination.defaultPageSize
        maxPageSize = this@toPublicSettings.pagination.maxPageSize
    }

    publicSettings.features.apply {
        allowDelete = this@toPublicSettings.features.allowDelete
        showTimestamps = this@toPublicSettings.features.showTimestamps
        readOnly = this@toPublicSettings.features.readOnly
    }

    publicSettings.localeConfig.apply {
        defaultLanguage = this@toPublicSettings.localeConfig.defaultLanguage
        timezone = this@toPublicSettings.localeConfig.timezone
    }

    publicSettings.telemetryConfig.apply {
        cloudUrl = this@toPublicSettings.telemetryConfig.cloudUrl
        enabled = this@toPublicSettings.telemetryConfig.enabled
    }

    return publicSettings
}