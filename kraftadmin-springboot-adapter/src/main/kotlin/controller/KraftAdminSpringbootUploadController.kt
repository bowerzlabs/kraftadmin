package com.kraftadmin.controller

import com.kraftadmin.utils.files.AdminStorageProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("\${kraftadmin.base-path:/admin}/api/uploads")
class KraftAdminSpringbootUploadController(
    private val storageProvider: AdminStorageProvider
) {

    @PostMapping
    fun uploadFile(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("oldUrl", required = false) oldUrl: String?
    ): ResponseEntity<Map<String, String>> {

        // 1. Delete the old file if it exists and belongs to our system
        if (!oldUrl.isNullOrBlank() && oldUrl.startsWith("/admin/files/")) {
            storageProvider.delete(oldUrl)
        }

        // 2. Upload the new file
        val newUrl = storageProvider.upload(file.bytes, file.originalFilename ?: "file", "admin")

        return ResponseEntity.ok(mapOf("url" to newUrl))
    }
}