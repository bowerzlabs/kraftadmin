package com.kraftadmin.controller

import com.kraftadmin.utils.files.AdminStorageProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("\${kraftadmin.base-path:/admin}/api/uploads")
@ConditionalOnProperty(prefix = "kraftpulse", name = ["enabled"], havingValue = "true")
class KraftAdminSpringbootUploadController(
    private val storageProvider: AdminStorageProvider
) {

    @PostMapping
    fun uploadFile(
        @RequestParam(value = "file", required = false) file: MultipartFile?,
        @RequestParam(value = "files", required = false) files: List<MultipartFile>?,
        @RequestParam("oldUrl", required = false) oldUrl: String?
    ): ResponseEntity<Map<String, Any>> {

        // Handle single file
        if (file != null) {
            if (!oldUrl.isNullOrBlank() && oldUrl.startsWith("/admin/files/")) {
                storageProvider.delete(oldUrl)
            }
            val url = storageProvider.upload(file.bytes, file.originalFilename ?: "file", "admin")
            return ResponseEntity.ok(mapOf("url" to url))
        }

        // Handle multiple files
        if (files != null) {
            val urls = files.map { f ->
                storageProvider.upload(f.bytes, f.originalFilename ?: "file", "admin")
            }
            return ResponseEntity.ok(mapOf("urls" to urls))
        }

        return ResponseEntity.badRequest().build()
    }

    @DeleteMapping
    fun deleteFile(@RequestBody request: Map<String, String>): ResponseEntity<Void> {
        val url = request["url"] ?: return ResponseEntity.badRequest().build()

        //  only delete files that are managed by configured storage provider
        if (url.startsWith("/admin/files/")) {
            storageProvider.delete(url)
            return ResponseEntity.noContent().build()
        }
        return ResponseEntity.badRequest().build()
    }
}