package com.kraftadmin.controller

import com.kraftadmin.config.SpringKraftAdminProperties
import com.kraftadmin.persistence.service.KraftSettingsService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/admin/api/settings")
class KraftSettingsController(
    private val settingsService: KraftSettingsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * GET the current merged configuration.
     * Svelte calls this to populate the "Settings" forms.
     */
    @GetMapping
    fun getSettings(): ResponseEntity<SpringKraftAdminProperties> {
        return ResponseEntity.ok(settingsService.getCurrentProperties())
    }

    /**
     * POST updated configuration from the UI.
     * This triggers the merge and the file persistence.
     */
//    @PostMapping
//    fun updateSettings(@RequestBody newSettings: SpringKraftAdminProperties): ResponseEntity<Map<String, String>> {
//        return try {
//            logger.info("KraftAdmin: Receiving UI settings update for title: ${newSettings.title}")
//
//            // 1. Trigger the merge and file save
//            settingsService.updateSettings(newSettings)
//
//            // 2. Return a friendly response for the Svelte toast notification
//            ResponseEntity.ok(mapOf("message" to "Settings updated successfully!"))
//        } catch (e: Exception) {
//            logger.error("Failed to update KraftAdmin settings", e)
//            ResponseEntity.internalServerError().body(mapOf("error" to "Failed to save settings: ${e.message}"))
//        }
//    }

    @PostMapping
    fun updateSettings(@RequestBody newSettings: SpringKraftAdminProperties): ResponseEntity<SpringKraftAdminProperties> {
        logger.info("KraftAdmin: Receiving UI settings update for title: ${newSettings.title}")
        val updated = settingsService.updateSettings(newSettings)
        return ResponseEntity.ok(updated)
    }
}