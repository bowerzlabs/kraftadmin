package controller

import persistence.service.KraftSettingsService
import dtos.PublicKraftAdminSettings
import dtos.SettingsUpdateRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/admin/api/settings")
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true")
class KraftSettingsController(
    private val settingsService: KraftSettingsService
) {

    /**
     * GET the current merged configuration.
     * Svelte calls this to populate the "Settings" forms.
     */
    @GetMapping
    fun getSettings(): ResponseEntity<PublicKraftAdminSettings> =
        ResponseEntity.ok(settingsService.getPublicSettings())

    /**
     * POST updated configuration from the UI.
     * This triggers the merge and the file persistence.
     */
    @PostMapping
    fun updateSettings(@RequestBody request: SettingsUpdateRequest): ResponseEntity<PublicKraftAdminSettings> {
        return ResponseEntity.ok(settingsService.updateSettings(request))
    }

}